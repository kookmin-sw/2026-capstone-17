import logging


def configure_logging(level: str) -> None:
    root_logger = logging.getLogger()
    if root_logger.handlers:
        return

    resolved_level = getattr(logging, level.upper(), logging.INFO)
    logging.basicConfig(
        level=resolved_level,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
