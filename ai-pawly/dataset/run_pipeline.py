"""
run_pipeline.py
===============
End-to-end dataset preparation pipeline.

Steps:
  1. generate   → raw/synthetic.jsonl
  2. clean      → processed/clean.jsonl
  3. split      → splits/train.jsonl, validation.jsonl, test.jsonl
  4. quality    → splits/reports/quality_report.json

Usage:
    python run_pipeline.py [--count 2000] [--seed 42] [--strict]
"""

import argparse
import subprocess
import sys
import os
import time


def run_step(name: str, cmd: list[str], cwd: str) -> None:
    print(f"\n{'='*60}")
    print(f"STEP: {name}")
    print(f"{'='*60}")
    start = time.time()
    result = subprocess.run(cmd, cwd=cwd, capture_output=False)
    elapsed = time.time() - start
    if result.returncode != 0:
        print(f"\n❌ Step '{name}' failed (exit {result.returncode})")
        sys.exit(result.returncode)
    print(f"✓ {name} completed in {elapsed:.1f}s")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--count",  type=int, default=2000)
    parser.add_argument("--seed",   type=int, default=42)
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()

    here = os.path.dirname(os.path.abspath(__file__))

    run_step(
        "Generate synthetic dataset",
        [sys.executable, "generate_synthetic.py",
         "--output", "raw/synthetic.jsonl",
         "--count",  str(args.count),
         "--seed",   str(args.seed)],
        cwd=here
    )

    clean_cmd = [sys.executable, "clean_and_anonymise.py",
                 "--input",  "raw/synthetic.jsonl",
                 "--output", "processed/clean.jsonl"]
    if args.strict:
        clean_cmd.append("--strict")

    run_step("Clean and anonymise", clean_cmd, cwd=here)

    run_step(
        "Stratified split",
        [sys.executable, "split_dataset.py",
         "--input",       "processed/clean.jsonl",
         "--output-dir",  "splits",
         "--train-ratio", "0.70",
         "--val-ratio",   "0.15",
         "--seed",        str(args.seed)],
        cwd=here
    )

    run_step(
        "Quality check",
        [sys.executable, "quality_check.py",
         "--splits-dir",  "splits",
         "--output-dir",  "splits/reports"],
        cwd=here
    )

    print(f"\n{'='*60}")
    print("✅  Pipeline complete.")
    print(f"    Train:      splits/train.jsonl")
    print(f"    Validation: splits/validation.jsonl")
    print(f"    Test:       splits/test.jsonl")
    print(f"    Report:     splits/reports/quality_report.json")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
