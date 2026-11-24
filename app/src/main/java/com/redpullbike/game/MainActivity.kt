package com.redpullbike.game

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = 5894 or 1024 or 512 or 4096
        val state = GameState(this)
        val root = FrameLayout(this)
        val gl = GLSurfaceView(this)
        gl.setEGLContextClientVersion(2)
        gl.setRenderer(RoadRenderer(state))
        gl.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        root.addView(gl)
        root.addView(GameHud(this, state))
        setContentView(root)
    }
}

private class GameState(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("redpull", 0)
    var screen = 0
    var speed = 0f
    var distance = 0f
    var coins = prefs.getInt("coins", 0)
    var bike = prefs.getInt("bike", 0)
    var holding = false
    var paused = false
    private var last = System.nanoTime()
    val names = listOf("RED SPORT", "R15 STYLE", "NINJA STYLE", "STREET X")
    val prices = listOf(0, 350, 800, 1200)
    val unlocked = prefs.getStringSet("unlocked", setOf("0"))!!.toMutableSet()
    fun save() { prefs.edit().putInt("coins", coins).putInt("bike", bike).putStringSet("unlocked", unlocked).apply() }
    fun update() {
        val now = System.nanoTime()
        val dt = ((now-last)/1000000000.0).toFloat().coerceIn(0f, 0.05f)
        last = now
        if (screen == 1 && !paused) {
            speed = if (holding) (speed + 75f*dt).coerceAtMost(260f) else (speed - 32f*dt).coerceAtLeast(0f)
            distance += speed/3.6f*dt
        }
    }
}

private class RoadRenderer(private val state: GameState) : GLSurfaceView.Renderer {
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var program = 0
    private var position = 0
    private var matrix = 0
    private var color = 0
    private lateinit var cube: FloatBuffer
    private val vertices = floatArrayOf(
        -.5f,-.5f,-.5f, .5f,-.5f,-.5f, .5f,.5f,-.5f, -.5f,-.5f,-.5f, .5f,.5f,-.5f, -.5f,.5f,-.5f,
        -.5f,-.5f,.5f, .5f,.5f,.5f, .5f,-.5f,.5f, -.5f,-.5f,.5f, -.5f,.5f,.5f, .5f,.5f,.5f,
        -.5f,-.5f,-.5f, -.5f,.5f,-.5f, -.5f,.5f,.5f, -.5f,-.5f,-.5f, -.5f,.5f,.5f, -.5f,-.5f,.5f,
        .5f,-.5f,-.5f, .5f,-.5f,.5f, .5f,.5f,.5f, .5f,-.5f,-.5f, .5f,.5f,.5f, .5f,.5f,-.5f,
        -.5f,.5f,-.5f, .5f,.5f,-.5f, .5f,.5f,.5f, -.5f,.5f,-.5f, .5f,.5f,.5f, -.5f,.5f,.5f,
        -.5f,-.5f,-.5f, .5f,-.5f,.5f, .5f,-.5f,-.5f, -.5f,-.5f,-.5f, -.5f,-.5f,.5f, .5f,-.5f,.5f
    )
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        val vs = "attribute vec3 p; uniform mat4 m; void main(){gl_Position=m*vec4(p,1.0);}"
        val fs = "precision mediump float; uniform vec4 c; void main(){gl_FragColor=c;}"
        fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            return shader
        }
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(program)
        position = GLES20.glGetAttribLocation(program, "p")
        matrix = GLES20.glGetUniformLocation(program, "m")
        color = GLES20.glGetUniformLocation(program, "c")
        cube = ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(vertices)
        cube.position(0)
        GLES20.glClearColor(0.48f,0.75f,0.92f,1f)
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0,0,width,height)
        Matrix.perspectiveM(projection,0,60f,width.toFloat()/height.coerceAtLeast(1),0.1f,150f)
    }
    override fun onDrawFrame(gl: GL10?) {
        state.update()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        Matrix.setLookAtM(view,0,0f,4f,9f,0f,1f,-6f,0f,1f,0f)
        GLES20.glUseProgram(program)
        val road = if (state.speed > 200f) floatArrayOf(.02f,.02f,.025f,1f) else floatArrayOf(.16f,.17f,.19f,1f)
        draw(0f,-.2f,-40f,9f,.35f,100f,road)
        draw(-5f,-.18f,-40f,1f,.2f,100f,floatArrayOf(.65f,.62f,.48f,1f))
        draw(5f,-.18f,-40f,1f,.2f,100f,floatArrayOf(.65f,.62f,.48f,1f))
        for (i in 0..16) {
            val z = -(i*6f + (state.distance*0.65f)%6f)
            draw(0f,-.01f,z,.13f,.03f,2.4f,floatArrayOf(1f,.92f,.72f,1f))
            for (side in listOf(-1f,1f)) {
                val x = side*7.3f
                draw(x,.5f,z,.25f,1f,.3f,floatArrayOf(.3f,.2f,.12f,1f))
                draw(x,1.25f,z,1.1f,1.1f,1.1f,floatArrayOf(.12f,.42f,.17f,1f))
            }
        }
        draw(-12f,-.5f,-35f,12f,.08f,100f,floatArrayOf(.05f,.42f,.72f,1f))
        val bikeColor = when(state.bike) {
            1 -> floatArrayOf(.05f,.22f,.85f,1f)
            2 -> floatArrayOf(.05f,.05f,.06f,1f)
            3 -> floatArrayOf(.7f,.72f,.75f,1f)
            else -> floatArrayOf(.9f,.025f,.04f,1f)
        }
        draw(0f,.55f,.9f,.65f,.12f,.65f,floatArrayOf(.02f,.02f,.025f,1f))
        draw(0f,.55f,-.8f,.65f,.12f,.65f,floatArrayOf(.02f,.02f,.025f,1f))
        draw(0f,.95f,.05f,.32f,.22f,1.55f,floatArrayOf(.16f,.16f,.17f,1f))
        draw(0f,1.25f,-.25f,.9f,.48f,.85f,bikeColor)
        draw(0f,1.55f,-.6f,.3f,.28f,.28f,floatArrayOf(.8f,.85f,.9f,1f))
        draw(0f,1.95f,.1f,.48f,.78f,.45f,floatArrayOf(.08f,.1f,.15f,1f))
        draw(0f,2.48f,.05f,.42f,.42f,.42f,floatArrayOf(.8f,.66f,.53f,1f))
        draw(0f,2.52f,-.12f,.46f,.2f,.45f,floatArrayOf(.03f,.04f,.05f,1f))
    }
    private fun draw(x: Float,y: Float,z: Float,sx: Float,sy: Float,sz: Float,c: FloatArray) {
        Matrix.setIdentityM(model,0)
        Matrix.translateM(model,0,x,y,z)
        Matrix.scaleM(model,0,sx,sy,sz)
        Matrix.multiplyMM(mvp,0,view,0,model,0)
        Matrix.multiplyMM(mvp,0,projection,0,mvp,0)
        GLES20.glUniformMatrix4fv(matrix,1,false,mvp,0)
        GLES20.glUniform4fv(color,1,c,0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,0,cube)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36)
        GLES20.glDisableVertexAttribArray(position)
    }
}

private class GameHud(ctx: Context, private val state: GameState) : View(ctx) {
    private val paint = Paint(3)
    private fun label(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int = Color.WHITE) {
        paint.color=color
        paint.textSize=size
        paint.typeface=android.graphics.Typeface.create("sans-serif-black",1)
        paint.setShadowLayer(4f,2f,2f,Color.BLACK)
        canvas.drawText(value,x,y,paint)
        paint.clearShadowLayer()
    }
    private fun button(canvas: Canvas, value: String, l: Float, t: Float, r: Float, b: Float) {
        paint.color=0xFFE52D3A.toInt()
        canvas.drawRoundRect(l,t,r,b,18f,18f,paint)
        label(canvas,value,l+20f,(t+b)/2f+9f,23f)
    }
    override fun onDraw(canvas: Canvas) {
        val w=width.toFloat()
        val h=height.toFloat()
        if(state.screen==1) {
            paint.color=0xCC101820.toInt()
            canvas.drawRoundRect(18f,18f,250f,112f,18f,18f,paint)
            label(canvas,state.speed.toInt().toString()+" KM/H",38f,70f,36f,if(state.speed>150) Color.RED else Color.WHITE)
            label(canvas,"RPM "+(state.speed*42).toInt(),40f,98f,16f)
            paint.color=0xCC101820.toInt()
            canvas.drawRoundRect(w-165f,18f,w-18f,82f,18f,18f,paint)
            label(canvas,if(state.paused) "RESUME" else "Ⅱ PAUSE",w-145f,59f,22f)
            label(canvas,"COINS "+state.coins,w-190f,h-25f,20f,Color.YELLOW)
            label(canvas,"DISTANCE "+(state.distance/1000f).toString().take(4)+" KM",22f,h-24f,18f)
            label(canvas,"OLD MAN: HA HA!",w*.42f,62f,24f,Color.YELLOW)
            if(state.speed>200f) label(canvas,"EXTREME BLACK ROAD MODE",w*.35f,100f,22f,Color.RED)
            if(state.paused) {
                paint.color=0xDD101820.toInt()
                canvas.drawRoundRect(w*.32f,h*.32f,w*.68f,h*.68f,22f,22f,paint)
                label(canvas,"PAUSED",w*.43f,h*.48f,36f)
                label(canvas,"TAP TO RESUME",w*.38f,h*.57f,22f)
            }
        } else {
            paint.color=0xEE101820.toInt()
            canvas.drawRoundRect(w*.18f,h*.1f,w*.82f,h*.9f,26f,26f,paint)
            label(canvas,"BIKE RED PULL",w*.32f,h*.25f,40f,0xFFFF5555.toInt())
            if(state.screen==0) {
                label(canvas,"3D HIGHWAY RIDE",w*.38f,h*.34f,22f)
                button(canvas,"PLAY",w*.36f,h*.43f,w*.64f,h*.57f)
                button(canvas,"BIKE SHOP",w*.36f,h*.62f,w*.64f,h*.76f)
                label(canvas,"COINS "+state.coins,w*.43f,h*.84f,20f,Color.YELLOW)
            } else {
                label(canvas,state.names[state.bike],w*.38f,h*.43f,28f)
                label(canvas,"PRICE "+state.prices[state.bike]+" COINS",w*.37f,h*.52f,20f)
                button(canvas,"NEXT BIKE",w*.35f,h*.59f,w*.65f,h*.70f)
                button(canvas,if(state.unlocked.contains(state.bike.toString())) "SELECT" else "UNLOCK",w*.35f,h*.72f,w*.65f,h*.83f)
                label(canvas,"BACK",w*.20f,h*.85f,18f)
            }
        }
        postInvalidateDelayed(33)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x=event.x
        val y=event.y
        val w=width.toFloat()
        val h=height.toFloat()
        if(event.action==MotionEvent.ACTION_DOWN) {
            when(state.screen) {
                0 -> if(y>h*.40f && y<h*.59f) { state.screen=1;state.speed=0f;state.distance=0f }
                     else if(y>h*.60f && y<h*.78f) state.screen=2
                2 -> if(y>h*.57f && y<h*.71f) state.bike=(state.bike+1)%state.names.size
                     else if(y>h*.71f && y<h*.86f) {
                         val id=state.bike.toString()
                         if(state.unlocked.contains(id)) state.save()
                         else if(state.coins>=state.prices[state.bike]) {
                             state.coins-=state.prices[state.bike]
                             state.unlocked.add(id)
                             state.save()
                         }
                     } else if(x<w*.3f) state.screen=0
                1 -> if(x>w-180f && y<120f) state.paused=!state.paused
                     else if(state.paused) state.paused=false
                     else state.holding=true
            }
            invalidate()
            return true
        }
        if(event.action==MotionEvent.ACTION_UP || event.action==MotionEvent.ACTION_CANCEL) {
            state.holding=false
            return true
        }
        return true
    }
}
