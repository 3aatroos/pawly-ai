"""
modeling.py
===========
Build the BitsAndBytes 4-bit config, the PEFT LoRA config, and load either
the QLoRA training model or the frozen base plus a saved adapter.
"""

from __future__ import annotations

from config import (
    EOS_TOKEN,
    MIN_TRANSFORMERS,
    PAD_TOKEN,
    QWEN2_MIN_TRANSFORMERS,
    QLoRAConfig,
    VOCAB_SIZE,
    parse_version,
)


def assert_runtime_versions() -> dict:
    missing = []
    versions = {}
    for name in ("torch", "transformers", "peft", "accelerate"):
        try:
            module = __import__(name)
        except ImportError:
            missing.append(name)
            continue
        versions[name] = getattr(module, "__version__", "unknown")
    if missing:
        raise RuntimeError(
            "Missing packages: "
            + ", ".join(missing)
            + ". Install them from ai-pawly/qlora/requirements.txt."
        )
    found = parse_version(versions["transformers"])
    if found < MIN_TRANSFORMERS:
        raise RuntimeError(
            f"transformers {versions['transformers']} is too old. "
            f"Qwen2 requires >= {'.'.join(map(str, QWEN2_MIN_TRANSFORMERS))} "
            f"and this project requires >= {'.'.join(map(str, MIN_TRANSFORMERS))}."
        )
    return versions


def assert_tokenizer_compatible(tokenizer) -> None:
    """Check the public Qwen2 tokenizer contract without trusting remote code."""
    name = tokenizer.__class__.__name__
    if "Qwen2" not in name:
        raise RuntimeError(f"Expected a Qwen2 tokenizer, got {name}.")
    pad = getattr(tokenizer, "pad_token", None)
    eos = getattr(tokenizer, "eos_token", None)
    if pad != PAD_TOKEN:
        raise RuntimeError(
            f"Expected pad token {PAD_TOKEN!r}, got {pad!r}. "
            "The official tokenizer uses <|endoftext|>, which is distinct from eos."
        )
    if eos != EOS_TOKEN:
        raise RuntimeError(f"Expected eos token {EOS_TOKEN!r}, got {eos!r}.")
    vocab = len(tokenizer)
    if vocab < VOCAB_SIZE:
        raise RuntimeError(f"Tokenizer vocab {vocab} is smaller than {VOCAB_SIZE}.")
    if hasattr(tokenizer, "is_fast") and not tokenizer.is_fast:
        raise RuntimeError("Qwen2TokenizerFast is required so completion tokens can be masked.")
    tokenizer.padding_side = "right"
    if getattr(tokenizer, "pad_token_id", None) is None:
        raise RuntimeError("pad_token_id is missing.")


def build_bnb_config(compute_dtype):
    """4-bit NF4 config from the QLoRA paper, with double quantization."""
    from transformers import BitsAndBytesConfig

    return BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_use_double_quant=True,
        bnb_4bit_compute_dtype=compute_dtype,
    )


def build_lora_config(cfg: QLoRAConfig):
    from peft import LoraConfig, TaskType

    return LoraConfig(
        r=cfg.lora_r,
        lora_alpha=cfg.lora_alpha,
        lora_dropout=cfg.lora_dropout,
        bias="none",
        task_type=TaskType.CAUSAL_LM,
        target_modules=list(cfg.lora_target_modules),
    )


def _load_tokenizer(cfg: QLoRAConfig):
    from transformers import AutoTokenizer

    tokenizer = AutoTokenizer.from_pretrained(cfg.model_id, trust_remote_code=False)
    assert_tokenizer_compatible(tokenizer)
    return tokenizer


def load_base_model(cfg: QLoRAConfig, decision):
    """Load the frozen base. 4-bit on CUDA, fp32 on the explicit CPU fallback."""
    import torch
    from transformers import AutoModelForCausalLM

    if decision.load_in_4bit:
        compute = torch.bfloat16 if decision.bf16 else torch.float16
        model = AutoModelForCausalLM.from_pretrained(
            cfg.model_id,
            quantization_config=build_bnb_config(compute),
            device_map={"": "cuda:0"},
            torch_dtype=compute,
            trust_remote_code=False,
            low_cpu_mem_usage=True,
        )
        return model
    model = AutoModelForCausalLM.from_pretrained(
        cfg.model_id,
        device_map={"": "cpu"},
        torch_dtype=torch.float32,
        trust_remote_code=False,
        low_cpu_mem_usage=True,
    )
    return model


def load_for_training(cfg: QLoRAConfig, decision):
    """Attach a fresh LoRA adapter. Base parameters stay frozen."""
    from adapters import assert_only_lora_trainable

    tokenizer = _load_tokenizer(cfg)
    model = load_base_model(cfg, decision)
    if decision.load_in_4bit:
        from peft import get_peft_model, prepare_model_for_kbit_training

        model = prepare_model_for_kbit_training(
            model,
            use_gradient_checkpointing=cfg.gradient_checkpointing,
        )
        model = get_peft_model(model, build_lora_config(cfg))
    else:
        from peft import get_peft_model

        model = get_peft_model(model, build_lora_config(cfg))
        if cfg.gradient_checkpointing and hasattr(model, "enable_input_require_grads"):
            model.enable_input_require_grads()
            try:
                model.gradient_checkpointing_enable(
                    gradient_checkpointing_kwargs={"use_reentrant": False}
                )
            except TypeError:
                model.gradient_checkpointing_enable()
    model.config.use_cache = False
    if getattr(model, "config", None) is not None:
        model.config.pad_token_id = tokenizer.pad_token_id
    stats = assert_only_lora_trainable(model)
    return model, tokenizer, stats


def load_for_inference(cfg: QLoRAConfig, decision, adapter_dir: str):
    """Load the untouched base and the saved adapter. Adapters are not merged."""
    from peft import PeftModel

    tokenizer = _load_tokenizer(cfg)
    base = load_base_model(cfg, decision)
    model = PeftModel.from_pretrained(base, adapter_dir, is_trainable=False)
    model.eval()
    model.config.use_cache = True
    model.config.pad_token_id = tokenizer.pad_token_id
    return model, tokenizer


def generate_completion(model, tokenizer, example: dict, max_new_tokens: int) -> str:
    import torch
    from prepare_data import prompt_text

    prompt = prompt_text(example, tokenizer)
    encoded = tokenizer(prompt, return_tensors="pt", add_special_tokens=False)
    device = next(model.parameters()).device
    encoded = {key: value.to(device) for key, value in encoded.items()}
    with torch.inference_mode():
        generated = model.generate(
            **encoded,
            max_new_tokens=max_new_tokens,
            do_sample=False,
            pad_token_id=tokenizer.pad_token_id,
            eos_token_id=tokenizer.eos_token_id,
        )
    new_tokens = generated[0][encoded["input_ids"].shape[1] :]
    return tokenizer.decode(new_tokens, skip_special_tokens=True).strip()


def build_training_arguments(cfg: QLoRAConfig, decision, output_dir: str, overwrite: bool):
    from transformers import TrainingArguments

    fields = TrainingArguments.__dataclass_fields__
    if cfg.max_steps:
        strategy = "steps"
        step_count = max(1, int(cfg.max_steps))
    else:
        strategy = "epoch"
        step_count = None
    kwargs = {
        "output_dir": output_dir,
        "overwrite_output_dir": overwrite,
        "seed": cfg.seed,
        "data_seed": cfg.seed,
        "per_device_train_batch_size": cfg.per_device_train_batch_size,
        "per_device_eval_batch_size": cfg.per_device_train_batch_size,
        "gradient_accumulation_steps": cfg.gradient_accumulation_steps,
        "num_train_epochs": cfg.num_train_epochs,
        "learning_rate": cfg.learning_rate,
        "weight_decay": cfg.weight_decay,
        "warmup_ratio": cfg.warmup_ratio,
        "lr_scheduler_type": cfg.lr_scheduler_type,
        "logging_steps": cfg.logging_steps,
        "logging_first_step": True,
        "save_strategy": strategy,
        "load_best_model_at_end": True,
        "metric_for_best_model": "eval_loss",
        "greater_is_better": False,
        "fp16": decision.fp16,
        "bf16": decision.bf16,
        "gradient_checkpointing": cfg.gradient_checkpointing,
        "optim": decision.optim,
        "report_to": "none",
        "remove_unused_columns": False,
        "max_grad_norm": cfg.max_grad_norm,
        "dataloader_num_workers": 0,
        "save_total_limit": 2,
    }
    if cfg.max_steps:
        kwargs["max_steps"] = int(cfg.max_steps)
    if "eval_strategy" in fields:
        kwargs["eval_strategy"] = strategy
    else:
        kwargs["evaluation_strategy"] = strategy
    if step_count is not None:
        kwargs["eval_steps"] = step_count
        kwargs["save_steps"] = step_count
    if "gradient_checkpointing_kwargs" in fields and cfg.gradient_checkpointing:
        kwargs["gradient_checkpointing_kwargs"] = {"use_reentrant": False}
    if "save_only_model" in fields:
        kwargs["save_only_model"] = True
    return TrainingArguments(**kwargs)
