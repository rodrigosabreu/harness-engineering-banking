#!/usr/bin/env python3
"""Retrain trigger script for AI-DLC pipeline."""
import json
import argparse
import sys
from pathlib import Path

def should_retrain(feedback_file: str, drift_file: str, cost_file: str,
                   threshold_feedback_neg: float = 0.20,
                   threshold_drift: float = 0.05,
                   threshold_cost: float = 0.35) -> dict:
    
    reasons = []
    
    # Feedback negativo
    try:
        with open(feedback_file) as f:
            feedback = json.load(f)
        total = feedback.get("total", 0)
        negative = feedback.get("negative", 0)
        if total > 0:
            neg_rate = negative / total
            if neg_rate > threshold_feedback_neg:
                reasons.append(f"Feedback negativo: {neg_rate:.1%} > {threshold_feedback_neg:.1%}")
    except Exception as e:
        pass
    
    # Drift
    try:
        with open(drift_file) as f:
            drift = json.load(f)
        if drift.get("drift_detected"):
            reasons.append(f"Drift detectado: shift={drift.get('embedding_shift', 0):.4f}")
    except:
        pass
    
    # Custo
    try:
        with open(cost_file) as f:
            cost = json.load(f)
        cost_per_inf = cost.get("cost_per_inference_usd", 0)
        if cost_per_inf > threshold_cost:
            reasons.append(f"Custo por inferência: ${cost_per_inf:.4f} > ${threshold_cost:.4f}")
    except:
        pass
    
    should = len(reasons) > 0
    return {
        "should_retrain": should,
        "reasons": reasons,
        "timestamp": "2024-01-15T10:30:00Z"
    }

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AI-DLC Retrain Trigger")
    parser.add_argument("--feedback-report", required=True)
    parser.add_argument("--drift-report", required=True)
    parser.add_argument("--cost-report", required=True)
    parser.add_argument("--threshold-feedback-negative", type=float, default=0.20)
    parser.add_argument("--threshold-drift", type=float, default=0.05)
    parser.add_argument("--threshold-cost", type=float, default=0.35)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    
    result = should_retrain(
        args.feedback_report, args.drift_report, args.cost_report,
        args.threshold_feedback_negative, args.threshold_drift, args.threshold_cost
    )
    
    with open(args.output, "w") as f:
        json.dump(result, f, indent=2)
    
    if result["should_retrain"]:
        print(f"🔄 RETRAIN TRIGGERED: {', '.join(result['reasons'])}")
        sys.exit(0)  # Exit 0 = trigger retrain pipeline
    else:
        print("✅ No retrain needed")
        sys.exit(0)