import re
from abc import ABC, abstractmethod

# Last group is the only greedy one in order to get
# all the remaining chars
from correctness.state import State

operation_pattern = re.compile("(.*?), (.*?), (.*?), (.*)")


class Operation(ABC):
    """Is considered an Operation any action/operation that
       we want to collect from the LoadGenerator client

       Operation with the load generator looks like this:
       timestamp, thread, op_type, op

       timestamp format: 2017-09-12_12:20:40.447
    """

    def __init__(self, line: str, client_id):
        groups = operation_pattern.match(line).groups()

        self.timestamp = groups[0].strip()
        self.thread = groups[1].strip()
        self.op_type = groups[2].strip()
        self.op = groups[3].strip()

        self.client_id = client_id

    @abstractmethod
    def add_to_history(self, state: State):
        pass

    @abstractmethod
    def demultiplex(self, demultiplexer_state, line: str):
        pass
