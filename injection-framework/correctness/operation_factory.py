from correctness.liveness_operation import LivenessOperation
from correctness.operation import Operation
from correctness.read_operation import ReadOperation
from correctness.rm_operation import RemoveOperation
from correctness.tx_operation import TxOperation
from correctness.tx_read_operation import TxReadOperation
from correctness.tx_rm_operation import TxRemoveOperation
from correctness.tx_write_operation import TxWriteOperation
from correctness.update_version_operation import UpdateVersionOperation
from correctness.write_operation import WriteOperation


class OperationFactory:
    operations = {"Read": ReadOperation, "Write": WriteOperation,
                  "Rm": RemoveOperation, "TxSnap": TxOperation,
                  "TxOpt": TxOperation, "TxNest": TxOperation,
                  "TxAWA": TxOperation, "TxRead": TxReadOperation,
                  "TxWrite": TxWriteOperation, "TxRm": TxRemoveOperation,
                  "Version": UpdateVersionOperation,
                  "Liveness": LivenessOperation}

    @classmethod
    def create_operation(cls, line, client_id) -> Operation:
        op_type = line.split(',')[2].strip()
        op = cls.operations.get(op_type)(line, client_id)
        return op
