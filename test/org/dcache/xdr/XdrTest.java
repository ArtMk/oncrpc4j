/*
 * Copyright (c) 2009 - 2012 Deutsches Elektronen-Synchroton,
 * Member of the Helmholtz Association, (DESY), HAMBURG, GERMANY
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this program (see the file COPYING.LIB for more
 * details); if not, write to the Free Software Foundation, Inc.,
 * 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.dcache.xdr;

import java.nio.ByteOrder;
import java.util.Arrays;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.BuffersBuffer;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class XdrTest {

    private Buffer _buffer;


    @Before
    public void setUp() {
        _buffer = allocateBuffer(1024);
        _buffer.order(ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void testDecodeInt() {

        int value = 17;
        _buffer.putInt(value);

        Xdr xdr = new Xdr(_buffer);
        xdr.beginDecoding();

        assertEquals("Decode value incorrect", 17, xdr.xdrDecodeInt());
    }

    @Test
    public void testEncodeDecodeOpaque() {

        byte[] data = "some random data".getBytes();
        XdrEncodingStream encoder = new Xdr(_buffer);
        encoder.beginEncoding();
        encoder.xdrEncodeDynamicOpaque(data);
        encoder.endEncoding();

        XdrDecodingStream decoder = new Xdr(_buffer);
        decoder.beginDecoding();
        byte[] decoded = decoder.xdrDecodeDynamicOpaque();

        assertTrue("encoded/decoded data do not match", Arrays.equals(data, decoded));
    }


    @Test
    public void testDecodeBooleanTrue() {

        _buffer.putInt(1);

        Xdr xdr = new Xdr(_buffer);
        xdr.beginDecoding();
        assertTrue("Decoded value incorrect", xdr.xdrDecodeBoolean() );
    }

    @Test
    public void testEncodeDecodeBooleanTrue() {

        boolean value = true;
        XdrEncodingStream encoder = new Xdr(_buffer);
        encoder.beginEncoding();
        encoder.xdrEncodeBoolean(value);
        encoder.endEncoding();

        XdrDecodingStream decoder = new Xdr(_buffer);
        decoder.beginDecoding();

        boolean decoded = decoder.xdrDecodeBoolean();
        assertEquals("Decoded boolean value incorrect", value, decoded );
    }

    @Test
    public void testEncodeDecodeBooleanFalse() {

        boolean value = false;
        XdrEncodingStream encoder = new Xdr(_buffer);
        encoder.beginEncoding();
        encoder.xdrEncodeBoolean(value);
        encoder.endEncoding();

        XdrDecodingStream decoder = new Xdr(_buffer);
        decoder.beginDecoding();

        boolean decoded = decoder.xdrDecodeBoolean();
        assertEquals("Decoded boolean value incorrect", value, decoded );
    }

    @Test
    public void testDecodeBooleanFale() {

        _buffer.putInt(0);

        Xdr xdr = new Xdr(_buffer);
        xdr.beginDecoding();
        assertFalse("Decoded value incorrect", xdr.xdrDecodeBoolean() );
    }


    @Test
    public void testEncodeDecodeString() {

        String original = "some random data";
        XdrEncodingStream encoder = new Xdr(_buffer);
        encoder.beginEncoding();
        encoder.xdrEncodeString(original);
        encoder.endEncoding();

        XdrDecodingStream decoder = new Xdr(_buffer);
        decoder.beginDecoding();

        String decoded = decoder.xdrDecodeString();

        assertEquals("encoded/decoded string do not match", original, decoded);
    }

    @Test
    public void testEncodeDecodeEmptyString() {

        String original = "";
        XdrEncodingStream encoder = new Xdr(_buffer);
        encoder.beginEncoding();
        encoder.xdrEncodeString(original);
        encoder.endEncoding();

        XdrDecodingStream decoder = new Xdr(_buffer);
        decoder.beginDecoding();

        String decoded = decoder.xdrDecodeString();

        assertEquals("encoded/decoded string do not match", original, decoded);
    }

    @Test
    public void testEncodeDecodeNullString() {

        String original = null;
        XdrEncodingStream encoder = new Xdr(_buffer);
        encoder.beginEncoding();
        encoder.xdrEncodeString(original);
        encoder.endEncoding();

        XdrDecodingStream decoder = new Xdr(_buffer);
        decoder.beginDecoding();

        String decoded = decoder.xdrDecodeString();

        assertEquals("encoded/decoded string do not match", "", decoded);
    }

    @Test
    public void testEncodeDecodeLong() {

        long value = 7L << 32;
        XdrEncodingStream encoder = new Xdr(_buffer);
        encoder.beginEncoding();
        encoder.xdrEncodeLong(value);
        encoder.endEncoding();

        XdrDecodingStream decoder = new Xdr(_buffer);
        decoder.beginDecoding();

        long decoded = decoder.xdrDecodeLong();

        assertEquals("encoded/decoded long do not match", value, decoded);
    }

    @Test
    public void testEncodeDecodeMaxLong() {

        long value = Long.MAX_VALUE;
        XdrEncodingStream encoder = new Xdr(_buffer);
        encoder.beginEncoding();
        encoder.xdrEncodeLong(value);
        encoder.endEncoding();

        XdrDecodingStream decoder = new Xdr(_buffer);
        decoder.beginDecoding();

        long decoded = decoder.xdrDecodeLong();

        assertEquals("encoded/decoded long do not match", value, decoded);
    }

    @Test
    public void testEncodeDecodeMinLong() {

        long value = Long.MIN_VALUE;
        XdrEncodingStream encoder = new Xdr(_buffer);
        encoder.beginEncoding();
        encoder.xdrEncodeLong(value);
        encoder.endEncoding();

        XdrDecodingStream decoder = new Xdr(_buffer);
        decoder.beginDecoding();

        long decoded = decoder.xdrDecodeLong();

        assertEquals("encoded/decoded long do not match", value, decoded);
    }

    @Test
    public void testEncodeDecodeIntVector() {

        int vector[] = { 1, 2, 3, 4 };
        XdrEncodingStream encoder = new Xdr(_buffer);
        encoder.beginEncoding();
        encoder.xdrEncodeIntVector(vector);
        encoder.endEncoding();

        XdrDecodingStream decoder = new Xdr(_buffer);
        decoder.beginDecoding();

        int[] decoded = decoder.xdrDecodeIntVector();

        assertArrayEquals("encoded/decoded int array do not match", vector, decoded);
    }

    @Test
    public void testSizeConstructor() {

        Xdr xdr = new Xdr(1024);

        assertEquals("encode/decode buffer size mismatch", 1024, xdr.asBuffer().capacity());
    }

    @Test
    public void testAutoGrow() {
        Xdr xdr = new Xdr(10);
        xdr.beginEncoding();
        xdr.xdrEncodeLong(1);
        xdr.xdrEncodeLong(1);
    }

    @Test
    public void testAutoGrowWthCompositeBuffer() {
        CompositeBuffer buffer = BuffersBuffer.create();
        buffer.append( allocateBuffer(10));
        Xdr xdr = new Xdr(buffer);
        xdr.beginEncoding();
        xdr.xdrEncodeLong(1);
        xdr.xdrEncodeLong(1);
    }

    private static Buffer allocateBuffer(int size) {
        return GrizzlyMemoryManager.allocate(size);
    }
}
