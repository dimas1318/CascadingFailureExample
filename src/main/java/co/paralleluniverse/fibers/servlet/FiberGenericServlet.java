/*
 * COMSAT
 * Copyright (c) 2013-2014, Parallel Universe Software Co. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
 */
package co.paralleluniverse.fibers.servlet;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.SuspendableRunnable;
import java.io.IOException;
import java.util.Date;
import javax.servlet.AsyncContext;
import javax.servlet.GenericServlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Implements a {@link GenericServlet} that services requests using a fiber-per-request model.
 */
public abstract class FiberGenericServlet extends GenericServlet {
    private static final long serialVersionUID = 1L;
    private transient FiberServletConfig configAD;
    private transient FiberServletContext contextAD;
    private final ThreadLocal<AsyncContext> currentAsyncContext = new ThreadLocal<AsyncContext>();
    private int stackSize = -1;

    /**
     *
     * @return Wrapped version of the ServletConfig initiated by {@link #init(javax.servlet.ServletConfig) }
     */
    @Override
    public final ServletConfig getServletConfig() {
        return configAD;
    }

    /**
     * @inheritDoc
     *
     * @return Wrapped version of the ServletContext initiated by {@link #init(javax.servlet.ServletConfig) }
     */
    @Override
    public ServletContext getServletContext() {
        return contextAD;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        this.contextAD = new FiberServletContext(config.getServletContext(), currentAsyncContext);
        this.configAD = new FiberServletConfig(config, contextAD);

        String sss = config.getInitParameter("stack-size");
        if (sss != null)
            stackSize = Integer.parseInt(sss);

        this.init();
    }

    protected void setStackSize(int stackSize) {
        this.stackSize = stackSize;
    }

    protected int getStackSize() {
        return stackSize;
    }

    /**
     * @inheritDoc
     *
     * Called by the servlet container to allow the servlet to respond to a request.
     * This implementation puts the servlet in async mode and then spawns a fiber that calls
     * {@link #suspendableService} to service the request.
     */
    @Override
    public final void service(final ServletRequest req, final ServletResponse res) {
        req.setAttribute("org.apache.catalina.ASYNC_SUPPORTED", true);
        final AsyncContext ac = req.startAsync();
        ac.setTimeout(120000);
        final FiberServletRequest srad = wrapRequest(req);
        new Fiber(null, stackSize, new SuspendableRunnable() {
            @Override
            public void run() throws SuspendExecution, InterruptedException {
                try {
                    currentAsyncContext.set(ac);
                    suspendableService(srad, res);
                } catch (Exception ex) {
                    log("Exception in fiber servlet", ex);
                } finally {
                    if (req.isAsyncStarted())
                        ac.complete();
                    currentAsyncContext.set(null);
                }
            }
        }).start();
    }

    /**
     * Called by the servlet container to allow the servlet to respond to a request. See {@link javax.servlet.Servlet#service}.
     * This methods may call suspendable functions since it runs in fiber context.
     *
     * <p>
     * This method is declared abstract so subclasses, such as {@code FiberHttpServlet}, must override it.
     *
     * @param req the {@code ServletRequest} object that contains the client's request
     * @param res the {@code ServletResponse} object that will contain the servlet's response
     *
     * @exception ServletException if an exception occurs that interferes with the servlet's normal operation occurred
     * @exception IOException      if an input or output exception occurs
     */
    protected abstract void suspendableService(ServletRequest req, ServletResponse res)
            throws ServletException, IOException, SuspendExecution;

    FiberServletRequest wrapRequest(final ServletRequest req) {
        return new FiberServletRequest(req);
    }
}
