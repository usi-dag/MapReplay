package ch.usi.inf.dag.mapreplay.trace;

/**
 * Codec for compact replay instructions.
 * <p>
 * Current layout:
 * <p>
 *   bits 63..40  reserved
 *   bits 39..32  unsigned 8-bit opcode
 *   bits 31..0   signed 32-bit operand/index payload
 * <p>
 * This preserves the existing byte/int-in-long strategy while making the
 * unsigned opcode interpretation explicit.
 */


public final class TraceInstructionCodec {

    private static final int OPCODE_SHIFT = 32;
    private static final long OPCODE_MASK = 0xFFL;
    private static final long INT_MASK = 0xFFFF_FFFFL;

    private TraceInstructionCodec() {
        throw new AssertionError("No instances");
    }

    public static long encodeOpcodeOperand(int opcode, int operandIndex) {
        if (!TraceOpCodes.isValidUnsignedByteOpcode(opcode)) {
            throw new IllegalArgumentException("Opcode out of unsigned byte range: " + opcode);
        }

        return ((long) opcode << OPCODE_SHIFT) | (operandIndex & INT_MASK);
    }

    public static long encodeOpcodeOperand(TraceOpcode opcode, int operandIndex) {
        return encodeOpcodeOperand(opcode.opcode(), operandIndex);
    }

    public static int decodeOpcode(long instruction) {
        return (int) ((instruction >>> OPCODE_SHIFT) & OPCODE_MASK);
    }

    public static int decodeOperandIndex(long instruction) {
        return (int) instruction;
    }

    public static TraceOpcode decodeTraceOpcode(long instruction) {
        return TraceOpcode.fromOpcode(decodeOpcode(instruction));
    }

    /**
     * Legacy-compatible helper for old byte/int callers.
     * Prefer encodeOpcodeOperand(int, int) in new code.
     */
    @Deprecated
    public static long encodeByteInt(byte opcode, int operandIndex) {
        return encodeOpcodeOperand(Byte.toUnsignedInt(opcode), operandIndex);
    }

    /**
     * Legacy-compatible helper.
     * Prefer decodeOpcode(long), which returns an unsigned int in 0..255.
     */
    @Deprecated
    public static byte decodeByte(long instruction) {
        return (byte) decodeOpcode(instruction);
    }

    /**
     * Legacy-compatible helper.
     * Prefer decodeOperandIndex(long).
     */
    @Deprecated
    public static int decodeInt(long instruction) {
        return decodeOperandIndex(instruction);
    }
}