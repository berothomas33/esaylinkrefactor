package com.pax.commonlib.utils;

import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;

/**
 * Tool class for assembling Byte Array
 */
public class ByteArrayBuilder {
    private int totalLength = 0;
    private final List<ByteArrayEntry> entryList = new LinkedList<>();

    /**
     * Create ByteArrayBuilder
     */
    public ByteArrayBuilder() {
        // do nothing
    }

    /**
     * Append byte to builder
     * @param value byte value
     * @return this builder
     */
    public ByteArrayBuilder append(byte value) {
        return append(new byte[]{ value });
    }

    /**
     * Append int to builder
     * @param value int value, will be converted to byte type
     * @return this builder
     */
    public ByteArrayBuilder append(int value) {
        return append((byte) value);
    }

    /**
     * Append byte array to builder
     * @param array byte array
     * @return this builder
     */
    public ByteArrayBuilder append(@Nullable byte[] array) {
        if (array == null) {
            return this;
        }
        return append(array.length, array);
    }

    /**
     * Append byte to builder, and specifying the length of the appended byte array. If length is
     * greater than 1, then the left side of the byte will be padded with 0.<br>
     * For example, such an array already exists in the builder:<br>
     * {@code [1, 2, 3, 4]}<br>
     * Now execute {@code append(3, 6)} on this builder, then the array of this builder will
     * become like this:<br>
     * {@code [1, 2, 3, 4, 0, 0, 6]}
     *
     * @param length The length to append. If length is greater than 1, then the left side of the
     * value will be padded with 0.
     * @param value byte value
     * @return this builder
     */
    public ByteArrayBuilder append(@LenRange int length, byte value) {
        return append(length, new byte[]{ value });
    }

    /**
     * Append int to builder, and specifying the length of the appended byte array. If length is
     * greater than 1, then the left side of the int will be padded with 0.<br>
     * For example, such an array already exists in the builder:<br>
     * {@code [1, 2, 3, 4]}<br>
     * Now execute {@code append(3, 6)} on this builder, then the array of this builder will
     * become like this:<br>
     * {@code [1, 2, 3, 4, 0, 0, 6]}
     *
     * @param length The length to append. If length is greater than 1, then the left side of the
     * value will be padded with 0.
     * @param value int value
     * @return this builder
     */
    public ByteArrayBuilder append(@LenRange int length, int value) {
        return append(length, (byte) value);
    }

    /**
     * Append byte array to builder, and specifying the length of the appended byte array. If
     * {@code length} is greater than {@code array.length}, then the left side of the {@code array}
     * will be padded with 0.<br>
     * For example, such an array already exists in the builder:<br>
     * {@code [1, 2, 3, 4]}<br>
     * Now execute {@code append(6, new byte[]{9, 8, 7})} on this builder, then the array of this
     * builder will become like this:<br>
     * {@code [1, 2, 3, 4, 0, 0, 0, 9, 8, 7]}<br>
     * <br>
     * But if {@code length} is less than {@code array.length}, then the excess byte elements on
     * the left side of the {@code array} will be discarded.<br>
     * For example, such an array already exists in the builder:<br>
     * {@code [1, 2, 3, 4]}<br>
     * Now execute {@code append(3, new byte[]{9, 8, 7, 6, 5, 4})} on this builder, then the array
     * of this builder will become like this:<br>
     * {@code [1, 2, 3, 4, 6, 5, 4]}
     *
     * @param length The length to append. If {@code length} is greater than {@code array.length},
     * then the left side of the {@code array} will be padded with 0. But if {@code length} is
     * less than {@code array.length}, then the excess byte elements on the left side of the {@code
     * array} will be discarded.
     * @param array byte array
     * @return this builder
     */
    public ByteArrayBuilder append(@LenRange int length, @Nullable byte[] array) {
        return append(length, true, array);
    }

    /**
     * Append byte to builder, and specifying the length of the appended byte array. If length is
     * greater than 1, then the left side or right side of the byte will be padded with 0.<br>
     * For example, such an array already exists in the builder:<br>
     * {@code [1, 2, 3, 4]}<br>
     * Now execute {@code append(3, true, 6)} on this builder, then the array of this builder will
     * become like this:<br>
     * {@code [1, 2, 3, 4, 0, 0, 6]}
     * Or execute {@code append(3, false, 6)} on this builder, then the array of this builder will
     * become like this:<br>
     * {@code [1, 2, 3, 4, 6, 0, 0]}
     *
     * @param length The length to append. If length is greater than 1, then the left side or
     * right side of the value will be padded with 0.
     * @param isPaddingRight Whether to pad the value to the right. If {@code true} is passed in,
     * and the passed in {@code length} is not equal to 1, it will be padded with 0 on the left.<br>
     * If {@code false} is passed in, and the passed in {@code length} is not equal to 1, 0 will be
     * padded on the right.
     * @param value byte value
     * @return this builder
     */
    public ByteArrayBuilder append(@LenRange int length, boolean isPaddingRight, byte value) {
        return append(length, isPaddingRight, new byte[]{ value });
    }

    /**
     * Append int to builder, and specifying the length of the appended byte array. If length is
     * greater than 1, then the left side or right side of the int will be padded with 0.<br>
     * For example, such an array already exists in the builder:<br>
     * {@code [1, 2, 3, 4]}<br>
     * Now execute {@code append(3, true, 6)} on this builder, then the array of this builder will
     * become like this:<br>
     * {@code [1, 2, 3, 4, 0, 0, 6]}
     * Or execute {@code append(3, false, 6)} on this builder, then the array of this builder will
     * become like this:<br>
     * {@code [1, 2, 3, 4, 6, 0, 0]}
     *
     * @param length The length to append. If length is greater than 1, then the left side or
     * right side of the value will be padded with 0.
     * @param isPaddingRight Whether to pad the value to the right. If {@code true} is passed in,
     * and the passed in {@code length} is not equal to 1, it will be padded with 0 on the left.<br>
     * If {@code false} is passed in, and the passed in {@code length} is not equal to 1, 0 will be
     * padded on the right.
     * @param value int value
     * @return this builder
     */
    public ByteArrayBuilder append(@LenRange int length, boolean isPaddingRight, int value) {
        return append(length, isPaddingRight, (byte) value);
    }

    /**
     * Append byte array to builder, and specifying the length of the appended byte array. If
     * {@code length} is greater than {@code array.length}, then the left side or right of the
     * {@code array} will be padded with 0.<br>
     * For example, such an array already exists in the builder:<br>
     * {@code [1, 2, 3, 4]}<br>
     * Now execute {@code append(6, true, new byte[]{9, 8, 7})} on this builder, then the array of
     * this builder will become like this:<br>
     * {@code [1, 2, 3, 4, 0, 0, 0, 9, 8, 7]}<br>
     * Or execute {@code append(6, false, new byte[]{9, 8, 7})} on this builder, then the array
     * of this builder will become like this:<br>
     * {@code [1, 2, 3, 4, 9, 8, 7, 0, 0, 0]}<br>
     * <br>
     * But if {@code length} is less than {@code array.length}, then the excess byte elements on
     * the left side or right side of the {@code array} will be discarded.<br>
     * For example, such an array already exists in the builder:<br>
     * {@code [1, 2, 3, 4]}<br>
     * Now execute {@code append(3, true, new byte[]{9, 8, 7, 6, 5, 4})} on this builder, then the
     * array of this builder will become like this:<br>
     * {@code [1, 2, 3, 4, 6, 5, 4]}<br>
     * Or execute {@code append(3, false, new byte[]{9, 8, 7, 6, 5, 4})} on this builder, then
     * the array of this builder will become like this:<br>
     * {@code [1, 2, 3, 4, 9, 8, 7]}
     *
     * @param length The length to append. If {@code length} is greater than {@code array.length},
     * then the left side or right side of the {@code array} will be padded with 0. But if {@code
     * length} is less than {@code array.length}, then the excess byte elements on the left side
     * or right side of the {@code array} will be discarded.
     * @param isPaddingRight Whether to pad the value to the right. If {@code true} is passed in,
     * and the passed in {@code length} is greater than {@code array.length}, it will be padded
     * with 0 on the left, or the passed in {@code length} is less than {@code array.length}, the
     * excess byte elements on the left side of the {@code array} will be discarded.<br>
     * If {@code false} is passed in, and the passed in {@code length} is greater than {@code
     * array.length}, it will be padded with 0 on the right, or the passed in {@code length} is
     * less than {@code array.length}, the excess byte elements on the right side of the {@code
     * array} will be discarded.
     * @param array byte array
     * @return this builder
     */
    public ByteArrayBuilder append(@LenRange int length, boolean isPaddingRight, @Nullable byte[] array) {
        if (length <= 0) {
            // length cannot be negative or zero, ignore
            return this;
        }
        if (length + totalLength < 0) {
            // overflow, cannot append, ignore
            return this;
        }

        byte[] data = array;

        if (array == null) {
            // empty array
            data = new byte[0];
        }

        int srcPosition = 0;
        int destPosition = 0;

        if (length < data.length) {
            // need cut
            if (isPaddingRight) {
                srcPosition = data.length - length;
            } // else, srcPosition should be set to 0
        }

        if (length > data.length) {
            // need fill
            if (isPaddingRight) {
                destPosition = length - data.length;
            } // else, destPosition should be set to 0
        }

        // if length == data.length, do nothing

        int copyLength = Math.min(length, data.length);

        entryList.add(new ByteArrayEntry(data, length, srcPosition, destPosition, copyLength));
        totalLength += length;
        return this;
    }

    /**
     * Get byte array.
     * @return byte array
     */
    public byte[] build() {
        if (totalLength == 0 || entryList.isEmpty()) {
            return new byte[0];
        }
        byte[] output = new byte[totalLength];
        int index = 0;
        for (ByteArrayEntry entry : entryList) {
            // copy data
            System.arraycopy(entry.data, entry.srcPosition, output,
                    index + entry.destPosition, entry.copyLength);

            index += entry.length;
        }
        return output;
    }

    private static class ByteArrayEntry {
        private final byte[] data;
        private final int length;
        private final int srcPosition;
        private final int destPosition;
        private final int copyLength;

        public ByteArrayEntry(byte[] data, int length, int srcPosition, int destPosition, int copyLength) {
            this.data = data;
            this.length = length;
            this.srcPosition = srcPosition;
            this.destPosition = destPosition;
            this.copyLength = copyLength;
        }

        public byte[] getData() {
            return data;
        }

        public int getLength() {
            return length;
        }
    }

    @IntRange(from = 1, to = Integer.MAX_VALUE)
    private @interface LenRange {}
}
