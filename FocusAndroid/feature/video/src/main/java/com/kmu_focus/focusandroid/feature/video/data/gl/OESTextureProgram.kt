package com.kmu_focus.focusandroid.feature.video.data.gl

import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OESTextureProgram {

    companion object {
        private const val TAG = "OESTextureProgram"

        // glReadPixels는 GL 좌표계(좌하단 원점)로 읽으므로 FBO 이미지가 상하 반전됨.
        // uFlipY=1.0이면 Y 반전하여 FBO에 저장 → glReadPixels 결과가 정방향.
        // uContentScaleX/Y: 영상 종횡비 보정. fit 시 content 영역만 그리면 letter-box, FBO에 원본 비율 유지.
        private const val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec4 aPosition;
            layout(location = 1) in vec2 aTexCoord;
            uniform mat4 uTexMatrix;
            uniform float uFlipY;
            uniform float uContentScaleX;
            uniform float uContentScaleY;
            out vec2 vTexCoord;
            void main() {
                float y = mix(aPosition.y, -aPosition.y, uFlipY);
                gl_Position = vec4(aPosition.x * uContentScaleX, y * uContentScaleY, 0.0, 1.0);
                vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """

        // OES 텍스처용 Fragment Shader
        private const val FRAGMENT_SHADER_OES = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            in vec2 vTexCoord;
            uniform samplerExternalOES uTexture;
            out vec4 fragColor;
            void main() {
                fragColor = texture(uTexture, vTexCoord);
            }
        """

        // 2D 텍스처용 Fragment Shader (FBO → 화면)
        private const val FRAGMENT_SHADER_2D = """
            #version 300 es
            precision mediump float;
            in vec2 vTexCoord;
            uniform sampler2D uTexture;
            out vec4 fragColor;
            void main() {
                fragColor = texture(uTexture, vTexCoord);
            }
        """

        // 풀스크린 쿼드 (position xy + texcoord st)
        private val QUAD_VERTICES = floatArrayOf(
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f,
        )
    }

    private var oesProgramId = 0
    private var twoDProgramId = 0
    private var vaoId = 0
    private var vboId = 0

    private var oesTexMatrixLoc = 0
    private var oesTextureLoc = 0
    private var oesFlipYLoc = 0
    private var oesContentScaleXLoc = 0
    private var oesContentScaleYLoc = 0
    private var twoDTexMatrixLoc = 0
    private var twoDTextureLoc = 0
    private var twoDFlipYLoc = 0
    private var twoDContentScaleXLoc = 0
    private var twoDContentScaleYLoc = 0

    fun init() {
        oesProgramId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_OES)
        oesTexMatrixLoc = GLES30.glGetUniformLocation(oesProgramId, "uTexMatrix")
        oesTextureLoc = GLES30.glGetUniformLocation(oesProgramId, "uTexture")
        oesFlipYLoc = GLES30.glGetUniformLocation(oesProgramId, "uFlipY")
        oesContentScaleXLoc = GLES30.glGetUniformLocation(oesProgramId, "uContentScaleX")
        oesContentScaleYLoc = GLES30.glGetUniformLocation(oesProgramId, "uContentScaleY")

        twoDProgramId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_2D)
        twoDTexMatrixLoc = GLES30.glGetUniformLocation(twoDProgramId, "uTexMatrix")
        twoDTextureLoc = GLES30.glGetUniformLocation(twoDProgramId, "uTexture")
        twoDFlipYLoc = GLES30.glGetUniformLocation(twoDProgramId, "uFlipY")
        twoDContentScaleXLoc = GLES30.glGetUniformLocation(twoDProgramId, "uContentScaleX")
        twoDContentScaleYLoc = GLES30.glGetUniformLocation(twoDProgramId, "uContentScaleY")

        setupVAO()
    }

    // OES 텍스처를 FBO에 그리기 (Y 반전 + 종횡비 보정). contentScaleX/Y=1이면 보정 없음.
    fun drawOES(oesTextureId: Int, texMatrix: FloatArray, contentScaleX: Float = 1f, contentScaleY: Float = 1f) {
        GLES30.glUseProgram(oesProgramId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES30.glUniform1i(oesTextureLoc, 0)
        GLES30.glUniformMatrix4fv(oesTexMatrixLoc, 1, false, texMatrix, 0)
        GLES30.glUniform1f(oesFlipYLoc, 1.0f)
        GLES30.glUniform1f(oesContentScaleXLoc, contentScaleX)
        GLES30.glUniform1f(oesContentScaleYLoc, contentScaleY)

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    // 2D 텍스처(FBO)를 화면에 그리기 (Y 재반전, 풀스크린이므로 content scale=1)
    fun draw2D(textureId: Int) {
        GLES30.glUseProgram(twoDProgramId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(twoDTextureLoc, 0)
        GLES30.glUniform1f(twoDFlipYLoc, 1.0f)
        GLES30.glUniform1f(twoDContentScaleXLoc, 1.0f)
        GLES30.glUniform1f(twoDContentScaleYLoc, 1.0f)

        val identity = FloatArray(16).apply {
            this[0] = 1f; this[5] = 1f; this[10] = 1f; this[15] = 1f
        }
        GLES30.glUniformMatrix4fv(twoDTexMatrixLoc, 1, false, identity, 0)

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    // Canvas로 그린 오버레이 텍스처를 알파 블렌딩으로 위에 합성 (Y 반전 없음)
    fun draw2DBlend(textureId: Int) {
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        GLES30.glUseProgram(twoDProgramId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(twoDTextureLoc, 0)
        GLES30.glUniform1f(twoDFlipYLoc, 0.0f)
        GLES30.glUniform1f(twoDContentScaleXLoc, 1.0f)
        GLES30.glUniform1f(twoDContentScaleYLoc, 1.0f)

        val identity = FloatArray(16).apply {
            this[0] = 1f; this[5] = 1f; this[10] = 1f; this[15] = 1f
        }
        GLES30.glUniformMatrix4fv(twoDTexMatrixLoc, 1, false, identity, 0)

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        GLES30.glDisable(GLES30.GL_BLEND)
    }

    fun release() {
        GLES30.glDeleteProgram(oesProgramId)
        GLES30.glDeleteProgram(twoDProgramId)
        val vaos = intArrayOf(vaoId)
        GLES30.glDeleteVertexArrays(1, vaos, 0)
        val vbos = intArrayOf(vboId)
        GLES30.glDeleteBuffers(1, vbos, 0)
    }

    private fun setupVAO() {
        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vaoId = vaos[0]

        val vbos = IntArray(1)
        GLES30.glGenBuffers(1, vbos, 0)
        vboId = vbos[0]

        val vertexBuffer = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(QUAD_VERTICES)
            .also { it.position(0) }

        GLES30.glBindVertexArray(vaoId)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, QUAD_VERTICES.size * 4, vertexBuffer, GLES30.GL_STATIC_DRAW)

        // aPosition (location=0): stride=16, offset=0
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0)
        // aTexCoord (location=1): stride=16, offset=8
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8)

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun createProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)

        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("프로그램 링크 실패: $log")
        }

        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return program
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
            throw RuntimeException("셰이더 컴파일 실패: $log")
        }
        return shader
    }
}
