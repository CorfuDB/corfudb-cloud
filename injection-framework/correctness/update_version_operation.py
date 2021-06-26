from correctness.operation import Operation


class UpdateVersionOperation(Operation):
    """ Update thread latest version """

    def __init__(self, line, client_id):
        super().__init__(line, client_id)
        self.version = int(self.op)

    def demultiplex(self, demultiplexer_state, line):
        demultiplexer_state.update_latest_version(self.client_id, self.thread, line)

    def add_to_history(self, state):
        state.update_thread_latest_version(self.thread, self.version)
