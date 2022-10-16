package it.auties.protobuf.tool.util;

import org.apache.logging.log4j.Logger;

import static org.apache.logging.log4j.LogManager.getLogger;

public interface LogProvider {
    Logger log = getLogger(LogProvider.class);
}
