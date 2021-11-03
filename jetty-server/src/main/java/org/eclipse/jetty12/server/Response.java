//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty12.server;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.util.Callback;

/**
 * Response is the absolute minimum to efficiently communicate a request.
 */
public interface Response
{
    Request getRequest();

    int getStatus();

    void setStatus(int code);

    // TODO do we need getHeaders and getMutableHeaders? or just a way to switch a Mutable HttpFields to be Immutable?

    HttpFields.Mutable getHeaders();

    HttpFields.Mutable getTrailers();

    void write(boolean last, Callback callback, ByteBuffer... content);

    void push(MetaData.Request request);

    void whenCommitting(BiConsumer<Request, Response> onCommit);

    boolean isCommitted();

    void reset();

    default Response getWrapped()
    {
        return null;
    }

    Response getWrapper();

    void setWrapper(Response response);

    class Wrapper implements Response
    {
        private final Response _wrapped;

        public Wrapper(Response wrapped)
        {
            _wrapped = wrapped;
            _wrapped.setWrapper(this);
        }

        @Override
        public int getStatus()
        {
            return _wrapped.getStatus();
        }

        @Override
        public void setStatus(int code)
        {
            _wrapped.setStatus(code);
        }

        @Override
        public HttpFields.Mutable getHeaders()
        {
            return _wrapped.getHeaders();
        }

        @Override
        public HttpFields.Mutable getTrailers()
        {
            return _wrapped.getTrailers();
        }

        @Override
        public void write(boolean last, Callback callback, ByteBuffer... content)
        {
            _wrapped.write(last, callback, content);
        }

        @Override
        public void push(MetaData.Request request)
        {
            _wrapped.push(request);
        }

        @Override
        public void whenCommitting(BiConsumer<Request, Response> onCommit)
        {
            _wrapped.whenCommitting(onCommit);
        }

        @Override
        public boolean isCommitted()
        {
            return _wrapped.isCommitted();
        }

        @Override
        public void reset()
        {
            _wrapped.reset();
        }

        @Override
        public Response getWrapped()
        {
            return _wrapped.getWrapped();
        }

        @Override
        public Request getRequest()
        {
            return _wrapped.getRequest();
        }

        @Override
        public Response getWrapper()
        {
            return _wrapped.getWrapper();
        }

        @Override
        public void setWrapper(Response response)
        {
            _wrapped.setWrapper(response);
        }
    }
}