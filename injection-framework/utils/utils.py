import sys


class bcolors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKGREEN = '\033[92m'
    CYAN = '\033[96m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'


def print_color_header(msg):
    print(bcolors.OKBLUE + "\n******  " + bcolors.ENDC + msg)


def count_lines(path):
    """ Count number of lines in a file

    Args:
       path

    Returns:
       number of lines (int)

    """
    with open(path) as f:
        for i, l in enumerate(f, 1):
            pass
        return i


def print_color_title(msg) -> None:
    print("\n\n" + bcolors.OKBLUE + "********* "
          + bcolors.ENDC + bcolors.CYAN + bcolors.BOLD +
          msg + bcolors.ENDC + bcolors.ENDC + bcolors.OKBLUE +
          " *********" + bcolors.ENDC)


def print_percentage(current, total) -> None:
    percentage = (current / total * 100)
    print("processed operations: %d%%" % percentage, end='\r')
    sys.stdout.flush()
