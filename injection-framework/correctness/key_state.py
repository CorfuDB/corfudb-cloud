from bisect import bisect_left

from prettytable import PrettyTable
from sortedcontainers import SortedList


# Module functions
def get_short_thread_id(thread_id: str):
    return thread_id.split("-")[-1].split("]")[0]


def get_short_value(val):
    if val is None:
        return None

    return val[-3:]


def format_client_thread_id(client_id, thread_id):
    return "c%s (%s)" % (client_id, thread_id)


class KeyState:
    """ Represent the state of a key at latest version seen so far """

    def __init__(self, map_id, key_id):
        self.map_id = map_id
        self.key_id = key_id
        self.version = -1
        self.value = None

        # This is a heap
        self.history: SortedList = SortedList()
        self.thread_ids = set()

    def put(self, value, version, oldest_tx_version, thread_id, client_id):
        self.value = value
        self.version = version

        key_history = KeyHistoryEntry(value, version, thread_id, client_id)
        self.history.add(key_history)

        self.thread_ids.add((client_id, get_short_thread_id(thread_id)))

        # remove unecessary values
        # self.trimm_history(oldest_tx_version)

    def commit(self, value, version, oldest_tx_version,
               tx_start, tx_end, tx_id, client_id) -> None:
        # if (self.version is not None and version < self.version):
        #     raise ValueError("log updates are not in order: ", version)
        self.value = value
        self.version = version

        key_history = KeyHistoryEntry(self.value, self.version, tx_id,
                                      client_id, True, tx_start, tx_end, )

        self.history.add(key_history)
        self.thread_ids.add((client_id, get_short_thread_id(tx_id)))

    def get_at_version(self, version):
        mock_key_history = KeyHistoryEntry(None, version, None, None)

        index = bisect_left(self.history, mock_key_history)

        if len(self.history) == 0:
            return None

        # print(self.history)
        # we found the exact time stamp, take that one
        if (len(self.history) > index and
                self.history[index].version == version):
            return self.history[index].value

        # If index = 0 and it is not a perfect match,
        # it means we don't have a value yet
        if index == 0:
            return None

        # print("version_found: {}".format(self.history[index-1]))

        return self.history[index - 1].value

    def trimm_history(self, oldest_tx_version) -> None:
        while self.history[0].version < oldest_tx_version:
            self.history.remove(self.history[0])

    def generate_history_row(self, entry) -> list:
        def format_pretty(entry):
            output = ""

            if entry.value is None:
                output = output + "Rm"
            else:
                output = output + get_short_value(entry.value)

            if entry.from_tx:
                output = output + " *"

            return output

        row = list(map(
            lambda x: format_pretty(entry)
            if x == (entry.client_id,
                     get_short_thread_id(entry.thread_id)) else "",
            self.thread_ids))

        row = [entry.version] + row
        return row

    def generate_history(self) -> PrettyTable:
        """Create a table with key history

        Args:
           self

        Returns:
            pretty table representing history of the key

        """
        client_thread_id_list = list(map(
            lambda x: format_client_thread_id(x[0], x[1]),
            self.thread_ids))

        table = PrettyTable(["Version"] + client_thread_id_list)

        for history_entry in self.history:
            table.add_row(self.generate_history_row(history_entry))

        return table

    def print_history(self) -> None:
        print("*** History for %s:%s ***" % (self.map_id, self.key_id))
        table = self.generate_history()

        print(table)


class KeyHistoryEntry:
    def __init__(self, value, version,
                 thread_id, client_id, from_tx=False,
                 tx_start=None, tx_end=None):
        self.version = version
        self.value = value
        self.thread_id = thread_id
        self.client_id = client_id
        self.from_tx = from_tx
        self.tx_start = tx_start
        self.tx_end = tx_end

    def __str__(self):
        return "%s: %s" % (self.version, self.value)

    def __eq__(self, other):
        return self.version == other.version

    def __lt__(self, other):
        return self.version < other.version
