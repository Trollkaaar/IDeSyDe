import argparse
import asyncio
import logging

from forsyde.io.python import ForSyDeModel

from desyder.identification import identify_decision_models
from desyder.identification import choose_decision_models
from desyder.exploration import choose_explorer
from desyder.api import DeSyDeR


description = '''
DeSyDe - Analytical Design Space Exploration for ForSyDe
'''


def cli_entry():
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument('model',
                        type=str,
                        help='Input ForSyDe-IO model to DeSyDe')
    parser.add_argument('--verbosity',
                        type=str,
                        default="INFO",
                        help='''
                        CLI verbosity level, from most silent to most verbose:
                        CRITICAL, ERROR, INFO (default), DEBUG.

                        Default is INFO.

                        Note that capitalization is done internally, so
                        info and INFO are equally valid.
                        ''')
    args = parser.parse_args()
    logger = logging.getLogger('CLI')
    logger.setLevel(
        getattr(logging, args.verbosity.upper(), 'INFO')
    )
    consoleLogHandler = logging.StreamHandler()
    consoleLogHandler.setLevel(
        getattr(logging, args.verbosity.upper(), 'INFO')
    )
    consoleLogHandler.setFormatter(
        logging.Formatter('[%(levelname)s\t%(asctime)s] %(message)s')
    )
    logger.addHandler(consoleLogHandler)
    logger.debug('Arguments parsed')
    in_model = ForSyDeModel.from_file(args.model)
    logger.info('Model parsed')
    logger.debug('DeSyDeR API created')
    identified = identify_decision_models(in_model)
    logger.info(f'{len(identified)} Decision model(s) identified')
    logger.debug(f"Decision models identified: {identified}")
    models_chosen = choose_decision_models(identified)
    logger.info(f'{len(models_chosen)} Decision model(s) chosen')
    explorer_and_models = choose_explorer(models_chosen)
    logger.info(f'{len(explorer_and_models)} Explorer(s) and Model(s) chosen')
    if len(explorer_and_models) == 0:
        print('No model or explorer could be chosen. Exiting')
    elif len(explorer_and_models) == 1:
        (e, m) = explorer_and_models[0]  # there is only one.
        logger.info('Initiating design space exploration')
        out_model = e.explore(m, in_model)
        logger.info('Design space explored')
        logger.info(f'Writting output model out_{args.model}')
        out_model.write('out_' + args.model)
    else:
        print('More than one chosen model and explorer. Exiting')
    logging.info('Done')


if __name__ == "__main__":
    cli_entry()
