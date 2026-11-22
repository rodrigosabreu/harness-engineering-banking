#!/usr/bin/env python3
"""Drift detection script for AI-DLC pipeline."""
import numpy as np
import argparse
import json
import sys
from pathlib import Path

try:
    from scipy.spatial.distance import cosine
    from scipy import stats
    SCIPY_AVAILABLE = True
except ImportError:
    SCIPY_AVAILABLE = False
    print("⚠️  scipy not available, using simplified drift detection")

def detect_drift(baseline_file: str, current_file: str, threshold: float = 0.05) -> dict:
    try:
        baseline = np.load(baseline_file)
        current = np.load(current_file)
        
        # Mean embedding shift
        baseline_mean = np.mean(baseline, axis=0)
        current_mean = np.mean(current, axis=0)
        
        shift = cosine(baseline_mean, current_mean)
        
        # Distribution shift (KS test) if scipy available
        ks_stat = 0.0
        p_value = 1.0
        if SCIPY_AVAILABLE:
            ks_stat, p_value = stats.ks_2samp(
                np.linalg.norm(baseline, axis=1),
                np.linalg.norm(current, axis=1)
            )
        
        result = {
            "embedding_shift": float(shift),
            "ks_statistic": float(ks_stat),
            "p_value": float(p_value),
            "drift_detected": shift > threshold or p_value < 0.05,
            "threshold": threshold,
            "baseline_samples": len(baseline),
            "current_samples": len(current)
        }
        
        return result
        
    except Exception as e:
        return {"error": str(e), "drift_detected": True}

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AI-DLC Drift Detection")
    parser.add_argument("--baseline", required=True, help="Baseline embeddings .npy file")
    parser.add_argument("--current", required=True, help="Current embeddings .npy file")
    parser.add_argument("--threshold", type=float, default=0.05, help="Drift threshold (cosine)")
    parser.add_argument("--output", required=True, help="Output JSON file")
    args = parser.parse_args()
    
    result = detect_drift(args.baseline, args.current, args.threshold)
    
    with open(args.output, "w") as f:
        json.dump(result, f, indent=2)
    
    if result.get("drift_detected"):
        print(f"⚠️ DRIFT DETECTED: shift={result.get('embedding_shift', 0):.4f}")
        sys.exit(1)
    else:
        print(f"✅ No drift detected: shift={result.get('embedding_shift', 0):.4f}")
        sys.exit(0)