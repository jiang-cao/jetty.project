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

package org.eclipse.jetty.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpHeaderValue;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.io.ByteBufferAccumulator;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;

class MockStream implements Stream
{
    private final static Throwable SUCCEEDED = new Throwable();
    private final static Content DEMAND = new Content.Special() {};
    private final long nano = System.nanoTime();
    private final AtomicReference<Content> _content = new AtomicReference<>();
    private final AtomicReference<Throwable> _complete = new AtomicReference<>();
    private final ByteBufferAccumulator _accumulator = new ByteBufferAccumulator();
    private final AtomicReference<ByteBuffer> _out = new AtomicReference<>();
    private final Channel _channel;
    private final AtomicReference<MetaData.Response> _response = new AtomicReference<>();

    MockStream(Channel channel)
    {
        this(channel, true);
    }

    MockStream(Channel channel, boolean atEof)
    {
        _channel = channel;
        if (atEof)
            _content.set(Content.EOF);
    }

    MockStream(Channel channel, ByteBuffer content)
    {
        _channel = channel;
        _content.set(Content.from(content, true));
    }

    public boolean isDemanding()
    {
        return _content.get() == DEMAND;
    }

    public Runnable addContent(ByteBuffer buffer, boolean last)
    {
        return addContent((last && BufferUtil.isEmpty(buffer)) ? Content.EOF : Content.from(buffer, last));
    }

    public Runnable addContent(Content content)
    {
        content = _content.getAndSet(content);
        if (content == DEMAND)
            return _channel.onContentAvailable();
        else if (content != null)
            throw new IllegalStateException();
        return null;
    }

    public MetaData.Response getResponse()
    {
        return _response.get();
    }

    public ByteBuffer getResponseContent()
    {
        return _out.get();
    }

    public Throwable getFailure()
    {
        Throwable t = _complete.get();
        return t == SUCCEEDED ? null : t;
    }

    @Override
    public String getId()
    {
        return "teststream";
    }

    @Override
    public long getNanoTimeStamp()
    {
        return nano;
    }

    @Override
    public Content readContent()
    {
        Content content = _content.get();
        if (content == null || content == DEMAND)
            return null;

        Content next = null;
        if (content instanceof Content.Trailers || content.isLast())
            next = Content.EOF;
        else if (content.isSpecial())
            next = content;
        _content.set(next);

        return content;
    }

    @Override
    public void demandContent()
    {
        if (!_content.compareAndSet(null, DEMAND))
            _channel.onContentAvailable();
    }

    @Override
    public void send(MetaData.Response response, boolean last, Callback callback, ByteBuffer... content)
    {
        if (response != null)
        {
            MetaData.Response r = _response.getAndSet(response);
            if (r != null && r.getStatus() >= 200)
            {
                callback.failed(new IOException("already committed"));
                return;
            }

            if (response.getFields().contains(HttpHeader.CONNECTION, HttpHeaderValue.CLOSE.asString()) &&
                _channel.getMetaConnection() instanceof MockConnectionMetaData)
                ((MockConnectionMetaData)_channel.getMetaConnection()).notPersistent();
        }

        for (ByteBuffer buffer : content)
            _accumulator.copyBuffer(buffer);

        if (last)
        {
            if (!_out.compareAndSet(null, _accumulator.takeByteBuffer()))
            {
                if (response != null || content.length > 0)
                {
                    callback.failed(new IOException("EOF"));
                    return;
                }
            }
        }
        callback.succeeded();
    }

    @Override
    public boolean isPushSupported()
    {
        return false;
    }

    @Override
    public void push(MetaData.Request request)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isComplete()
    {
        return _complete.get() != null;
    }

    @Override
    public void succeeded()
    {
        _complete.compareAndSet(null, SUCCEEDED);
    }

    @Override
    public void failed(Throwable x)
    {
        if (_channel.getMetaConnection() instanceof MockConnectionMetaData)
            ((MockConnectionMetaData)_channel.getMetaConnection()).notPersistent();
        _complete.compareAndSet(null, x == null ? new Throwable() : x);
    }

    @Override
    public void upgrade(Connection connection)
    {

    }
}
