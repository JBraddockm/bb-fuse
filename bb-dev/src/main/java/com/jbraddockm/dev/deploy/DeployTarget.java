package com.jbraddockm.dev.deploy;

/** The device a deploy command runs against: its host, HTTPS port and password. */
record DeployTarget(String host, int port, String password) {}
