package com.kmu_focus.focusandroid.core.media.data.gl

import android.opengl.GLES30
import androidx.annotation.VisibleForTesting
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MosaicProgram {

    private var copyProgramId = 0
    private var kawaseProgramId = 0
    private var compositeProgramId = 0
    private var vaoId = 0
    private var vboId = 0

    private var copyTextureLoc = 0
    private var copySourceRectLoc = 0
    private var kawaseTextureLoc = 0
    private var kawaseTexelSizeLoc = 0
    private var kawaseOffsetLoc = 0
    private var compositeTextureLoc = 0
    private var compositeFaceCountLoc = 0
    private var compositeEllipseCenterLoc = 0
    private var compositeEllipseRadiusLoc = 0
    private var compositeEllipseAngleLoc = 0
    private var compositeRegionRectLoc = 0
    private val uniformCenters = FloatArray(MAX_FACES * 2)
    private val uniformRadii = FloatArray(MAX_FACES * 2)
    private val uniformAngles = FloatArray(MAX_FACES)
    private var previousFaceCount = 0

    fun init() {
        copyProgramId = createProgram(VERTEX_SHADER, COPY_FRAGMENT_SHADER)
        copyTextureLoc = GLES30.glGetUniformLocation(copyProgramId, "uTexture")
        copySourceRectLoc = GLES30.glGetUniformLocation(copyProgramId, "uSourceRect")

        kawaseProgramId = createProgram(VERTEX_SHADER, KAWASE_FRAGMENT_SHADER)
        kawaseTextureLoc = GLES30.glGetUniformLocation(kawaseProgramId, "uTexture")
        kawaseTexelSizeLoc = GLES30.glGetUniformLocation(kawaseProgramId, "uTexelSize")
        kawaseOffsetLoc = GLES30.glGetUniformLocation(kawaseProgramId, "uOffsetPx")

        compositeProgramId = createProgram(VERTEX_SHADER, COMPOSITE_FRAGMENT_SHADER)
        compositeTextureLoc = GLES30.glGetUniformLocation(compositeProgramId, "uTexture")
        compositeFaceCountLoc = GLES30.glGetUniformLocation(compositeProgramId, "uFaceCount")
        compositeEllipseCenterLoc = GLES30.glGetUniformLocation(compositeProgramId, "uEllipseCenter[0]")
        compositeEllipseRadiusLoc = GLES30.glGetUniformLocation(compositeProgramId, "uEllipseRadius[0]")
        compositeEllipseAngleLoc = GLES30.glGetUniformLocation(compositeProgramId, "uEllipseAngle[0]")
        compositeRegionRectLoc = GLES30.glGetUniformLocation(compositeProgramId, "uRegionRect")
        setupVao()
    }

    fun copyTextureRegion(
        inputTexId: Int,
        sourceRect: UvRect = UvRect.FULL,
    ) {
        if (copyProgramId == 0 || inputTexId == 0) return

        GLES30.glUseProgram(copyProgramId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexId)
        GLES30.glUniform1i(copyTextureLoc, 0)
        GLES30.glUniform4f(copySourceRectLoc, sourceRect.minX, sourceRect.minY, sourceRect.maxX, sourceRect.maxY)
        drawQuad()
    }

    fun applyKawaseBlur(
        inputTexId: Int,
        textureWidth: Int,
        textureHeight: Int,
        offsetPx: Float,
    ) {
        if (kawaseProgramId == 0 || inputTexId == 0 || textureWidth <= 0 || textureHeight <= 0) return

        GLES30.glUseProgram(kawaseProgramId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexId)
        GLES30.glUniform1i(kawaseTextureLoc, 0)
        GLES30.glUniform2f(kawaseTexelSizeLoc, 1f / textureWidth.toFloat(), 1f / textureHeight.toFloat())
        GLES30.glUniform1f(kawaseOffsetLoc, offsetPx)
        drawQuad()
    }

    fun compositeBlurredRegion(
        inputTexId: Int,
        ellipses: List<EllipseParams>,
        regionRect: UvRect,
    ) {
        if (compositeProgramId == 0 || inputTexId == 0) return

        val faceCount = updateUniformData(ellipses)
        if (faceCount == 0) return

        GLES30.glUseProgram(compositeProgramId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexId)
        GLES30.glUniform1i(compositeTextureLoc, 0)
        GLES30.glUniform1i(compositeFaceCountLoc, faceCount)
        GLES30.glUniform2fv(compositeEllipseCenterLoc, MAX_FACES, uniformCenters, 0)
        GLES30.glUniform2fv(compositeEllipseRadiusLoc, MAX_FACES, uniformRadii, 0)
        GLES30.glUniform1fv(compositeEllipseAngleLoc, MAX_FACES, uniformAngles, 0)
        GLES30.glUniform4f(
            compositeRegionRectLoc,
            regionRect.minX,
            regionRect.minY,
            regionRect.maxX,
            regionRect.maxY,
        )
        drawQuad()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun updateUniformData(ellipses: List<EllipseParams>): Int {
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

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getUniformCentersForTest(): FloatArray = uniformCenters

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getUniformRadiiForTest(): FloatArray = uniformRadii

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getUniformAnglesForTest(): FloatArray = uniformAngles

    fun release() {
        deleteProgramIfNeeded(copyProgramId)
        deleteProgramIfNeeded(kawaseProgramId)
        deleteProgramIfNeeded(compositeProgramId)
        copyProgramId = 0
        kawaseProgramId = 0
        compositeProgramId = 0
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

    private fun drawQuad() {
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun deleteProgramIfNeeded(programId: Int) {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
        }
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
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val MAX_FACES = 8

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

        private const val COPY_FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;

            in vec2 vTexCoord;
            uniform sampler2D uTexture;
            uniform vec4 uSourceRect;
            out vec4 fragColor;

            void main() {
                vec2 uv = vec2(
                    mix(uSourceRect.x, uSourceRect.z, vTexCoord.x),
                    mix(uSourceRect.y, uSourceRect.w, vTexCoord.y)
                );
                fragColor = texture(uTexture, uv);
            }
        """

        private const val KAWASE_FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;

            in vec2 vTexCoord;
            uniform sampler2D uTexture;
            uniform vec2 uTexelSize;
            uniform float uOffsetPx;
            out vec4 fragColor;

            void main() {
                vec2 offset = uTexelSize * uOffsetPx;
                vec4 color = texture(uTexture, vTexCoord) * 4.0;
                color += texture(uTexture, clamp(vTexCoord + vec2(-offset.x, -offset.y), vec2(0.0), vec2(1.0)));
                color += texture(uTexture, clamp(vTexCoord + vec2(offset.x, -offset.y), vec2(0.0), vec2(1.0)));
                color += texture(uTexture, clamp(vTexCoord + vec2(-offset.x, offset.y), vec2(0.0), vec2(1.0)));
                color += texture(uTexture, clamp(vTexCoord + vec2(offset.x, offset.y), vec2(0.0), vec2(1.0)));
                fragColor = color / 8.0;
            }
        """

        private const val COMPOSITE_FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            #define MAX_FACES 8

            in vec2 vTexCoord;
            uniform sampler2D uTexture;
            uniform int uFaceCount;
            uniform vec2 uEllipseCenter[MAX_FACES];
            uniform vec2 uEllipseRadius[MAX_FACES];
            uniform float uEllipseAngle[MAX_FACES];
            uniform vec4 uRegionRect;
            out vec4 fragColor;

            bool isMasked(vec2 uv) {
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
                if (!isMasked(vTexCoord)) {
                    discard;
                }
                vec2 regionSize = max(uRegionRect.zw - uRegionRect.xy, vec2(0.000001));
                vec2 localUv = (vTexCoord - uRegionRect.xy) / regionSize;
                if (localUv.x < 0.0 || localUv.x > 1.0 || localUv.y < 0.0 || localUv.y > 1.0) {
                    discard;
                }
                fragColor = texture(uTexture, localUv);
            }
        """

        private val QUAD_VERTICES = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f
        )
    }
}
