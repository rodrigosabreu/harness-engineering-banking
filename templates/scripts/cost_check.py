#!/usr/bin/env python3
"""Cost check script for AI-DLC pipeline."""
import json
import sys
import argparse
from pathlib import Path

def check_cost(threshold_usd: float, usage_file: str = "evals/usage.json") -> bool:
    """Verifica se custo por inferência está abaixo do threshold."""
    try:
        with open(usage_file) as f:
            data = json.load(f)
        
        total_cost = data.get("total_cost_usd", 0)
        num_inferences = data.get("num_inferences", 1)
        cost_per_inference = total_cost / num_inferences
        
        print(f"Total cost: ${total_cost:.4f}")
        print(f"Inferences: {num_inferences}")
        print(f"Cost per inference: ${cost_per_inference:.4f}")
        print(f"Threshold: ${threshold_usd:.4f}")
        
        if cost_per_inference > threshold_usd:
            print(f"❌ FAIL: Cost per inference (${cost_per_inference:.4f}) exceeds threshold (${threshold_usd:.4f})")
            return False
        else:
            print(f"✅ PASS: Cost per inference within threshold")
            return True
            
    except FileNotFoundError:
        print(f"⚠️  Usage file not found: {usage_file}")
        print("Skipping cost check")
        return True
    except Exception as e:
        print(f"❌ ERROR: {e}")
        return False

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AI-DLC Cost Check")
    parser.add_argument("--threshold", type=float, default=0.30, help="Max cost per inference (USD)")
    parser.add_argument("--usage-file", type=str, default="evals/usage.json", help="Usage data file")
    args = parser.parse_args()
    
    success = check_cost(args.threshold, args.usage_file)
    sys.exit(0 if success else 1)