package com.kmu_focus.focusandroid.feature.video.data.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MosaicProgram {

    private var programId = 0
    private var vaoId = 0
    private var vboId = 0

    private var textureLoc = 0
    private var faceCountLoc = 0
    private var ellipseCenterLoc = 0
    private var ellipseRadiusLoc = 0
    private var ellipseAngleLoc = 0
    private var blockSizeLoc = 0
    private val uniformCenters = FloatArray(MAX_FACES * 2)
    private val uniformRadii = FloatArray(MAX_FACES * 2)
    private val uniformAngles = FloatArray(MAX_FACES)
    private var previousFaceCount = 0

    fun init() {
        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        textureLoc = GLES30.glGetUniformLocation(programId, "uTexture")
        faceCountLoc = GLES30.glGetUniformLocation(programId, "uFaceCount")
        ellipseCenterLoc = GLES30.glGetUniformLocation(programId, "uEllipseCenter[0]")
        ellipseRadiusLoc = GLES30.glGetUniformLocation(programId, "uEllipseRadius[0]")
        ellipseAngleLoc = GLES30.glGetUniformLocation(programId, "uEllipseAngle[0]")
        blockSizeLoc = GLES30.glGetUniformLocation(programId, "uBlockSize")
        setupVao()
    }

    fun draw(
        inputTexId: Int,
        ellipses: List<EllipseParams>,
        blockSize: Float,
        viewWidth: Int,
        viewHeight: Int
    ) {
        if (programId == 0 || inputTexId == 0) return

        val faceCount = updateUniformData(ellipses)
        val blockSizeX = normalizeBlockSizeX(blockSize, viewWidth)
        val blockSizeY = normalizeBlockSizeY(blockSize, viewHeight)

        GLES30.glUseProgram(programId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexId)
        GLES30.glUniform1i(textureLoc, 0)
        GLES30.glUniform1i(faceCountLoc, faceCount)
        GLES30.glUniform2fv(ellipseCenterLoc, MAX_FACES, uniformCenters, 0)
        GLES30.glUniform2fv(ellipseRadiusLoc, MAX_FACES, uniformRadii, 0)
        GLES30.glUniform1fv(ellipseAngleLoc, MAX_FACES, uniformAngles, 0)
        GLES30.glUniform2f(blockSizeLoc, blockSizeX, blockSizeY)

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    internal fun updateUniformData(ellipses: List<EllipseParams>): Int {
        if (previousFaceCount > 0) {
            for (index in 0 until previousFaceCount) {
                val base = index * 2
                uniformCenters[base] = 0f
                uniformCenters[base + 1] = 0f
                uniformRadii[base] = 0f
                uniformRadii[base + 1] = 0f
                uniformAngles[index] = 0f
            }
        }

        val faceCount = minOf(ellipses.size, MAX_FACES)
        for (index in 0 until faceCount) {
            val ellipse = ellipses[index]
            val base = index * 2
            uniformCenters[base] = ellipse.centerX
            uniformCenters[base + 1] = ellipse.centerY
            uniformRadii[base] = ellipse.radiusX
            uniformRadii[base + 1] = ellipse.radiusY
            uniformAngles[index] = ellipse.angle
        }
        previousFaceCount = faceCount
        return faceCount
    }

    internal fun getUniformCentersForTest(): FloatArray = uniformCenters

    internal fun getUniformRadiiForTest(): FloatArray = uniformRadii

    internal fun getUniformAnglesForTest(): FloatArray = uniformAngles

    fun release() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
        if (vaoId != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
            vaoId = 0
        }
        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
    }

    private fun setupVao() {
        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vaoId = vaos[0]

        val vbos = IntArray(1)
        GLES30.glGenBuffers(1, vbos, 0)
        vboId = vbos[0]

        val buffer = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(QUAD_VERTICES)
            .also { it.position(0) }

        GLES30.glBindVertexArray(vaoId)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            QUAD_VERTICES.size * 4,
            buffer,
            GLES30.GL_STATIC_DRAW
        )

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8)

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun createProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)

        val createdProgramId = GLES30.glCreateProgram()
        GLES30.glAttachShader(createdProgramId, vertexShader)
        GLES30.glAttachShader(createdProgramId, fragmentShader)
        GLES30.glLinkProgram(createdProgramId)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(createdProgramId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(createdProgramId)
            GLES30.glDeleteProgram(createdProgramId)
            throw RuntimeException("Mosaic program link failed: $log")
        }

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        return createdProgramId
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Mosaic shader compile failed: $log")
        }

        return shader
    }

    companion object {
        internal const val MAX_FACES = 8

        private const val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec4 aPosition;
            layout(location = 1) in vec2 aTexCoord;
            out vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition.xy, 0.0, 1.0);
                vTexCoord = aTexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            #define MAX_FACES 8

            in vec2 vTexCoord;
            uniform sampler2D uTexture;

            uniform int uFaceCount;
            uniform vec2 uEllipseCenter[MAX_FACES];
            uniform vec2 uEllipseRadius[MAX_FACES];
            uniform float uEllipseAngle[MAX_FACES];
            uniform vec2 uBlockSize;

            out vec4 fragColor;

            bool isInsideAnyEllipse(vec2 uv) {
                for (int i = 0; i < MAX_FACES; i++) {
                    if (i >= uFaceCount) break;
                    vec2 d = uv - uEllipseCenter[i];
                    float cosA = cos(-uEllipseAngle[i]);
                    float sinA = sin(-uEllipseAngle[i]);
                    vec2 r = vec2(d.x * cosA - d.y * sinA, d.x * sinA + d.y * cosA);
                    vec2 safeRadius = max(uEllipseRadius[i], vec2(0.000001));
                    vec2 n = r / safeRadius;
                    if (dot(n, n) <= 1.0) return true;
                }
                return false;
            }

            void main() {
                if (isInsideAnyEllipse(vTexCoord)) {
                    vec2 safeBlockSize = max(uBlockSize, vec2(0.000001));
                    vec2 block = floor(vTexCoord / safeBlockSize) * safeBlockSize + safeBlockSize * 0.5;
                    fragColor = texture(uTexture, block);
                } else {
                    fragColor = texture(uTexture, vTexCoord);
                }
            }
        """

        private val QUAD_VERTICES = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f
        )

        internal fun normalizeBlockSizeX(blockPixels: Float, viewWidth: Int): Float {
            if (viewWidth <= 0) return 1f
            return blockPixels.coerceAtLeast(1f) / viewWidth.toFloat()
        }

        internal fun normalizeBlockSizeY(blockPixels: Float, viewHeight: Int): Float {
            if (viewHeight <= 0) return 1f
            return blockPixels.coerceAtLeast(1f) / viewHeight.toFloat()
        }
    }
}
