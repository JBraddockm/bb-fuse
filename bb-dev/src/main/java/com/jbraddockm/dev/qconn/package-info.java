/**
 * A pure-JDK client for a device's qconn agent (TCP :8000): the control protocol (greeting, info,
 * service list, service select) and the services built on it. The dev-mode deploy sync uses its
 * {@code file} service to push assets into an app's sandbox.
 */
package com.jbraddockm.dev.qconn;
