from correctness.operation import Operation


class LivenessOperation(Operation):
    """This operation is reporting the state of LoadGenerator Liveness
       at the end of the run. There only going to be one of these
       operation per client.

       format: Liveness, [Success|Fail]
    """

    def __init__(self, line, client_id):
        super().__init__(line, client_id)
        success_str = self.op.split(",")[-1].strip()
        self.success = True if success_str == "Success" else False

    def add_to_history(self, state):
        """ No-op """

    def demultiplex(self, demultiplexer_state, line):
        demultiplexer_state.liveness_report_operation(line)
