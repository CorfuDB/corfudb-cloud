import sys
from os import path

from correctness.verify_correctness import CorrectnessVerificator


def verify():
    print("Start verification!")

    log_file = "correctness.log"
    correctness_logs = [log_file]

    if not path.exists(log_file):
        print("Correctness log file not found")
        sys.exit(2)

    verification = CorrectnessVerificator(correctness_logs, console=True, verbose=False, print_report=True)
    report = verification.verify()

    if not report.correctness_success:
        print()
        print("Correctness error")
        sys.exit(1)

    if not report.liveness_success:
        print()
        print("Liveness error")
        sys.exit(1)


if __name__ == "__main__":
    verify()
