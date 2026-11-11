/**
 * Collecting the device's diagnostic logs over {@code support.cgi}.
 *
 * <p>The one command here, {@link com.jbraddockm.link.endpoint.support.DumpDeviceLogs}, drives the
 * CGI's <em>DumpLog</em> facility: it selects the facility, waits while the device gathers a fixed
 * set of system logs with {@code quiplog}, and streams the resulting {@code cso-logs-report.zip} (a
 * {@code bbswltLog} archive) to a file. The capture is a general whole-device diagnostic bundle,
 * not BlackBerry-Link-specific; bb-link streams it verbatim and does not look inside. The CGI's
 * second facility, BugMiner (an interactive bug-report session), is out of scope — see {@code
 * analysis/endpoints/SupportCGI.md}.
 */
package com.jbraddockm.link.endpoint.support;
