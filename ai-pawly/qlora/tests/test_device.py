"""GPU selection, explicit CPU fallback, and the memory gate."""

import builtins

from config import QLoRAConfig
from device import (
    MemorySnapshot,
    apply_bitsandbytes_availability,
    decide_backend,
    probe_memory,
)


def _snapshot(**overrides) -> MemorySnapshot:
    payload = dict(
        cuda_available=False,
        gpu_name=None,
        total_vram_gb=None,
        free_vram_gb=None,
        bf16_supported=False,
        total_ram_gb=32.0,
        free_ram_gb=16.0,
    )
    payload.update(overrides)
    return MemorySnapshot(**payload)


def test_this_machine_reports_ram_and_an_explicit_backend():
    snapshot = probe_memory()
    assert snapshot.total_ram_gb is not None and snapshot.total_ram_gb > 0
    assert snapshot.free_ram_gb is not None
    assert snapshot.free_ram_gb <= snapshot.total_ram_gb
    decision = decide_backend(snapshot, QLoRAConfig())
    assert decision.name in {"qlora_4bit", "lora_cpu"}
    if not snapshot.cuda_available:
        assert decision.device == "cpu"
        assert decision.load_in_4bit is False
        assert decision.fp16 is False
        assert decision.bf16 is False
        assert "Explicit CPU fallback" in decision.reason
        assert "quantization disabled" in decision.reason


def test_cuda_with_enough_vram_selects_4bit_qlora():
    decision = decide_backend(
        _snapshot(
            cuda_available=True,
            gpu_name="Test GPU",
            total_vram_gb=4.0,
            free_vram_gb=3.0,
            bf16_supported=False,
        ),
        QLoRAConfig(),
    )
    assert decision.name == "qlora_4bit"
    assert decision.load_in_4bit is True
    assert decision.device == "cuda"
    assert decision.optim == "paged_adamw_8bit"
    assert decision.fp16 is True
    assert decision.bf16 is False
    assert "4-bit NF4 quantization enabled" in decision.reason


def test_bf16_gpu_does_not_also_enable_fp16():
    decision = decide_backend(
        _snapshot(
            cuda_available=True,
            gpu_name="BF16 GPU",
            total_vram_gb=12.0,
            free_vram_gb=10.0,
            bf16_supported=True,
        ),
        QLoRAConfig(),
    )
    assert decision.bf16 is True
    assert decision.fp16 is False


def test_low_vram_is_an_explicit_cpu_fallback():
    decision = decide_backend(
        _snapshot(
            cuda_available=True,
            gpu_name="Small GPU",
            total_vram_gb=4.0,
            free_vram_gb=2.99,
            bf16_supported=True,
        ),
        QLoRAConfig(),
    )
    assert decision.load_in_4bit is False
    assert decision.device == "cpu"
    assert decision.optim == "adamw_torch"
    assert "Explicit CPU fallback" in decision.reason
    assert "2.99" in decision.reason


def test_cpu_flag_overrides_a_capable_gpu():
    decision = decide_backend(
        _snapshot(
            cuda_available=True,
            gpu_name="Test GPU",
            total_vram_gb=24.0,
            free_vram_gb=20.0,
            bf16_supported=True,
        ),
        QLoRAConfig(),
        force_cpu=True,
    )
    assert decision.device == "cpu"
    assert decision.load_in_4bit is False
    assert "--cpu" in decision.reason


def test_low_host_ram_blocks_before_load():
    cpu = decide_backend(_snapshot(free_ram_gb=2.0), QLoRAConfig())
    assert cpu.blocked is True
    assert "free RAM" in cpu.reason
    gpu = decide_backend(
        _snapshot(
            cuda_available=True,
            gpu_name="Test GPU",
            total_vram_gb=8.0,
            free_vram_gb=7.0,
            bf16_supported=True,
            free_ram_gb=1.0,
        ),
        QLoRAConfig(),
    )
    assert gpu.blocked is True
    assert "host RAM" in gpu.reason


def test_missing_bitsandbytes_forces_the_cpu_path(monkeypatch):
    decision = decide_backend(
        _snapshot(
            cuda_available=True,
            gpu_name="Test GPU",
            total_vram_gb=8.0,
            free_vram_gb=7.0,
            bf16_supported=True,
        ),
        QLoRAConfig(),
    )
    assert decision.load_in_4bit is True
    real_import = builtins.__import__

    def guarded(name, globals=None, locals=None, fromlist=(), level=0):
        if name == "bitsandbytes":
            raise ImportError("missing")
        return real_import(name, globals, locals, fromlist, level)

    monkeypatch.setattr(builtins, "__import__", guarded)
    downgraded = apply_bitsandbytes_availability(decision, QLoRAConfig())
    assert downgraded.device == "cpu"
    assert downgraded.load_in_4bit is False
    assert "bitsandbytes" in downgraded.reason
    assert "Explicit CPU fallback" in downgraded.reason
