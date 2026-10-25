"""
device.py
=========
GPU QLoRA path and an explicit CPU fallback, with a memory check
before any model weights are loaded.

4-bit BitsAndBytes quantization is enabled only when CUDA is available,
bitsandbytes imports, and free VRAM covers the configured minimum.
Otherwise training continues as fp32 LoRA on CPU, and the reason is logged.
"""

from __future__ import annotations

import ctypes
import json
import os
from dataclasses import asdict, dataclass

from config import QLoRAConfig


@dataclass
class MemorySnapshot:
    cuda_available: bool
    gpu_name: str | None
    total_vram_gb: float | None
    free_vram_gb: float | None
    bf16_supported: bool
    total_ram_gb: float | None
    free_ram_gb: float | None

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class BackendDecision:
    name: str
    device: str
    load_in_4bit: bool
    optim: str
    fp16: bool
    bf16: bool
    blocked: bool
    reason: str
    snapshot: MemorySnapshot

    def to_dict(self) -> dict:
        payload = asdict(self)
        payload["snapshot"] = self.snapshot.to_dict()
        return payload


def _gb(num_bytes: int | None) -> float | None:
    if num_bytes is None:
        return None
    return round(num_bytes / (1024 ** 3), 3)


def read_ram_bytes() -> tuple[int, int]:
    """Return (total, available) physical RAM in bytes. (0, 0) if unknown."""
    if os.name == "nt":
        class MEMORYSTATUSEX(ctypes.Structure):
            _fields_ = [
                ("dwLength", ctypes.c_ulong),
                ("dwMemoryLoad", ctypes.c_ulong),
                ("ullTotalPhys", ctypes.c_ulonglong),
                ("ullAvailPhys", ctypes.c_ulonglong),
                ("ullTotalPageFile", ctypes.c_ulonglong),
                ("ullAvailPageFile", ctypes.c_ulonglong),
                ("ullTotalVirtual", ctypes.c_ulonglong),
                ("ullAvailVirtual", ctypes.c_ulonglong),
                ("ullAvailExtendedVirtual", ctypes.c_ulonglong),
            ]

        status = MEMORYSTATUSEX()
        status.dwLength = ctypes.sizeof(MEMORYSTATUSEX)
        if not ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
            return (0, 0)
        return (int(status.ullTotalPhys), int(status.ullAvailPhys))

    try:
        page = os.sysconf("SC_PAGE_SIZE")
        total = os.sysconf("SC_PHYS_PAGES") * page
        free = os.sysconf("SC_AVPHYS_PAGES") * page
        return (int(total), int(free))
    except (AttributeError, OSError, ValueError):
        return (0, 0)


def _probe_cuda() -> dict | None:
    try:
        import torch
    except ImportError:
        return None
    if not torch.cuda.is_available():
        return None
    free_bytes, total_bytes = torch.cuda.mem_get_info()
    return {
        "name": torch.cuda.get_device_name(0),
        "free": int(free_bytes),
        "total": int(total_bytes),
        "bf16": bool(torch.cuda.is_bf16_supported()),
    }


def probe_memory() -> MemorySnapshot:
    total_ram, free_ram = read_ram_bytes()
    cuda = _probe_cuda()
    ram_known = total_ram > 0
    return MemorySnapshot(
        cuda_available=cuda is not None,
        gpu_name=None if cuda is None else cuda["name"],
        total_vram_gb=None if cuda is None else _gb(cuda["total"]),
        free_vram_gb=None if cuda is None else _gb(cuda["free"]),
        bf16_supported=bool(cuda and cuda["bf16"]),
        total_ram_gb=_gb(total_ram) if ram_known else None,
        free_ram_gb=_gb(free_ram) if ram_known else None,
    )


def _cpu_decision(snapshot: MemorySnapshot, reason: str) -> BackendDecision:
    return BackendDecision(
        name="lora_cpu",
        device="cpu",
        load_in_4bit=False,
        optim="adamw_torch",
        fp16=False,
        bf16=False,
        blocked=False,
        reason=reason,
        snapshot=snapshot,
    )


def _apply_ram_gate(decision: BackendDecision, cfg: QLoRAConfig) -> BackendDecision:
    free = decision.snapshot.free_ram_gb
    if free is None:
        decision.reason += " Host RAM could not be read; the RAM floor was skipped."
        return decision
    if decision.device == "cpu" and free < cfg.min_free_ram_gb:
        decision.blocked = True
        decision.reason = (
            f"Refusing to load the model: free RAM is {free:.2f} GB, below the "
            f"{cfg.min_free_ram_gb:.1f} GB required for CPU LoRA. "
            "Explicit CPU fallback cannot run safely."
        )
        return decision
    if decision.device == "cuda" and free < cfg.min_host_ram_for_gpu_gb:
        decision.blocked = True
        decision.reason = (
            f"Refusing to load the model: free host RAM is {free:.2f} GB, below the "
            f"{cfg.min_host_ram_for_gpu_gb:.1f} GB required to stage the checkpoint "
            "before 4-bit QLoRA."
        )
    return decision


def decide_backend(
    snapshot: MemorySnapshot,
    cfg: QLoRAConfig,
    force_cpu: bool = False,
    force_reason: str | None = None,
) -> BackendDecision:
    """Choose 4-bit QLoRA on CUDA or an explicit fp32 LoRA CPU fallback."""
    if force_cpu:
        reason = force_reason or (
            "Explicit CPU fallback requested by --cpu. 4-bit quantization disabled."
        )
        decision = _cpu_decision(snapshot, reason)
    elif not snapshot.cuda_available:
        decision = _cpu_decision(
            snapshot,
            "Explicit CPU fallback: CUDA is unavailable, and BitsAndBytes 4-bit "
            "quantization requires a CUDA GPU. Training continues with LoRA in "
            "fp32 and quantization disabled.",
        )
    elif snapshot.free_vram_gb is None or snapshot.free_vram_gb < cfg.min_free_vram_gb:
        free = 0.0 if snapshot.free_vram_gb is None else snapshot.free_vram_gb
        decision = _cpu_decision(
            snapshot,
            "Explicit CPU fallback: free VRAM is "
            f"{free:.2f} GB, below the {cfg.min_free_vram_gb:.1f} GB required "
            "for 4-bit QLoRA. Training continues with LoRA in fp32 and "
            "quantization disabled.",
        )
    else:
        use_bf16 = snapshot.bf16_supported
        decision = BackendDecision(
            name="qlora_4bit",
            device="cuda",
            load_in_4bit=True,
            optim="paged_adamw_8bit",
            fp16=not use_bf16,
            bf16=use_bf16,
            blocked=False,
            reason=(
                f"CUDA GPU {snapshot.gpu_name}: {snapshot.free_vram_gb:.2f} GB free "
                f"of {snapshot.total_vram_gb:.2f} GB. 4-bit NF4 quantization enabled."
            ),
            snapshot=snapshot,
        )
    return _apply_ram_gate(decision, cfg)


def apply_bitsandbytes_availability(
    decision: BackendDecision,
    cfg: QLoRAConfig,
) -> BackendDecision:
    """Downgrade to the CPU path when 4-bit was selected but bitsandbytes is missing."""
    if not decision.load_in_4bit:
        return decision
    try:
        import bitsandbytes  # noqa: F401
    except ImportError:
        return decide_backend(
            decision.snapshot,
            cfg,
            force_cpu=True,
            force_reason=(
                "Explicit CPU fallback: bitsandbytes is not installed, so 4-bit "
                "quantization cannot run. Training continues with LoRA in fp32 "
                "and quantization disabled."
            ),
        )
    return decision


def memory_after_load() -> dict:
    """Second memory reading, called after the model is resident."""
    try:
        import torch
    except ImportError:
        return {"device": "unavailable"}
    total_ram, free_ram = read_ram_bytes()
    report = {
        "free_ram_gb": _gb(free_ram) if total_ram else None,
        "total_ram_gb": _gb(total_ram) if total_ram else None,
    }
    if not torch.cuda.is_available():
        report["device"] = "cpu"
        return report
    free_bytes, total_bytes = torch.cuda.mem_get_info()
    report.update(
        {
            "device": "cuda",
            "allocated_gb": round(torch.cuda.memory_allocated() / (1024 ** 3), 3),
            "reserved_gb": round(torch.cuda.memory_reserved() / (1024 ** 3), 3),
            "free_vram_gb": _gb(int(free_bytes)),
            "total_vram_gb": _gb(int(total_bytes)),
        }
    )
    return report


def main() -> int:
    snapshot = probe_memory()
    decision = decide_backend(snapshot, QLoRAConfig())
    print(json.dumps({"memory": snapshot.to_dict(), "backend": decision.to_dict()}, indent=2))
    return 2 if decision.blocked else 0


if __name__ == "__main__":
    raise SystemExit(main())
