package com.redpullbike.game

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class MainActivity : Activity() {
    private lateinit var game: ArrowPuzzleView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = 5894 or 1024 or 512 or 4096
        game = ArrowPuzzleView(this)
        setContentView(game)
    }
    override fun onResume() { super.onResume(); if (::game.isInitialized) game.resumeGame() }
    override fun onPause() { if (::game.isInitialized) game.pauseGame(); super.onPause() }
}

private data class ArrowTile(val id: Int, val x: Int, val y: Int, val dir: Int)
private data class GameLevel(val number: Int, val tiles: MutableList<ArrowTile>)

private class ArrowPuzzleView(context: Context) : View(context) {
    private val prefs = context.getSharedPreferences("arrow_puzzle_save", Context.MODE_PRIVATE)
    private val bgPaint = Paint(3)
    private val p = Paint(3)
    private val path = Path()
    private val density = resources.displayMetrics.density
    private var screen = 0
    private var level = prefs.getInt("level", 1).coerceIn(1, 10)
    private var coins = prefs.getInt("coins", 120)
    private var soundOn = prefs.getBoolean("sound", true)
    private var moves = 0
    private var hearts = 3
    private var score = prefs.getInt("score", 0)
    private var hints = 3
    private var message = "Clear every arrow to solve the puzzle"
    private var board = mutableListOf<ArrowTile>()
    private val history = ArrayDeque<List<ArrowTile>>()
    private var boardLeft = 0f
    private var boardTop = 0f
    private var cell = 0f
    private var gridSize = 6
    private var pulse = 0f
    private var lastHint = -1
    private var levelWon = false
    private var best = prefs.getInt("best_" + level, 0)

    private val bg1 = Color.rgb(10, 18, 42)
    private val bg2 = Color.rgb(25, 39, 76)
    private val cyan = Color.rgb(60, 226, 238)
    private val gold = Color.rgb(255, 198, 76)
    private val white = Color.rgb(245, 249, 255)
    private val muted = Color.rgb(159, 178, 211)
    private val tileColors = intArrayOf(
        Color.rgb(50, 205, 222), Color.rgb(110, 137, 255),
        Color.rgb(183, 112, 255), Color.rgb(255, 160, 85)
    )

    init { isFocusable = true; generateLevel() }

    fun pauseGame() {}
    fun resumeGame() { invalidate() }

    private fun save() {
        val e = prefs.edit().putInt("level", level).putInt("coins", coins).putInt("score", score).putBoolean("sound", soundOn)
        e.apply()
    }

    private fun generateLevel() {
        gridSize = 5
        val target = (9 + level).coerceAtMost(19)
        var chosen = mutableListOf<ArrowTile>()
        var attempt = 0
        while (attempt < 2500) {
            val random = Random(level * 971 + attempt * 31)
            val cells = (0 until gridSize * gridSize).shuffled(random).take(target)
            val candidate = cells.mapIndexed { i, n -> ArrowTile(i, n % gridSize, n / gridSize, random.nextInt(4)) }.toMutableList()
            if (isSolvable(candidate)) { chosen = candidate; break }
            attempt++
        }
        if (chosen.isEmpty()) {
            val fallback = mutableListOf<ArrowTile>()
            val perimeter = mutableListOf<Triple<Int,Int,Int>>()
            for (x in 0 until gridSize) perimeter.add(Triple(x,0,0))
            for (y in 1 until gridSize) perimeter.add(Triple(gridSize-1,y,1))
            for (x in gridSize-2 downTo 0) perimeter.add(Triple(x,gridSize-1,2))
            for (y in gridSize-2 downTo 1) perimeter.add(Triple(0,y,3))
            perimeter.take(target).forEachIndexed { i,v -> fallback.add(ArrowTile(i,v.first,v.second,v.third)) }
            chosen = fallback
        }
        board = chosen
        history.clear()
        moves = 0
        hearts = 3
        hints = 3
        lastHint = -1
        levelWon = false
        message = "Tap an arrow with a clear path"
        best = prefs.getInt("best_" + level, 0)
        invalidate()
    }

    private fun isSolvable(input: List<ArrowTile>): Boolean {
        val left = input.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            val ready = left.filter { canExit(it, left) }
            if (ready.isNotEmpty()) { left.removeAll(ready.toSet()); changed = true }
        }
        return left.isEmpty()
    }

    private fun canExit(tile: ArrowTile, tiles: List<ArrowTile>): Boolean {
        val dx = intArrayOf(0, 1, 0, -1)[tile.dir]
        val dy = intArrayOf(-1, 0, 1, 0)[tile.dir]
        var x = tile.x + dx
        var y = tile.y + dy
        while (x in 0 until gridSize && y in 0 until gridSize) {
            if (tiles.any { it.x == x && it.y == y }) return false
            x += dx; y += dy
        }
        return true
    }

    private fun removeTile(tile: ArrowTile) {
        history.addLast(board.toList())
        if (history.size > 20) history.removeFirst()
        board.removeAll { it.id == tile.id }
        moves++
        message = "Correct arrow! Keep going"
        lastHint = -1
        if (board.isEmpty()) {
            levelWon = true
            screen = 2
            val old = prefs.getInt("best_" + level, 0)
            val score = moves
            if (old == 0 || score < old) prefs.edit().putInt("best_" + level, score).apply()
            score += 10
            coins += 10
            message = "Level clear! +10 points, +10 coins"
            save()
        }
        invalidate()
    }

    private fun rounded(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int, stroke: Int? = null, sw: Float = 1f) {
        p.color = color; p.style = Paint.Style.FILL; p.shader = null; p.clearShadowLayer()
        c.drawRoundRect(l,t,r,b,radius,radius,p)
        if (stroke != null) { p.color=stroke; p.style=Paint.Style.STROKE; p.strokeWidth=sw; c.drawRoundRect(l,t,r,b,radius,radius,p); p.style=Paint.Style.FILL }
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int = white, align: Paint.Align = Paint.Align.LEFT, bold: Boolean = false) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = color; p.textSize = size
        p.textAlign = align; p.typeface = Typeface.create(if (bold) "sans-serif-black" else "sans-serif-medium", if (bold) Typeface.BOLD else Typeface.NORMAL)
        p.clearShadowLayer(); p.setShadowLayer(3f, 0f, 2f, Color.BLACK)
        c.drawText(s,x,y,p); p.clearShadowLayer()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        p.shader = LinearGradient(0f,0f,w,h,bg1,bg2,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,w,h,p); p.shader = null
        drawAmbient(canvas,w,h)
        when (screen) {
            0 -> drawHome(canvas,w,h)
            1 -> drawGame(canvas,w,h)
            2 -> drawWin(canvas,w,h)
            else -> drawGameOver(canvas,w,h)
        }
        pulse += 0.035f
        postInvalidateDelayed(40)
    }

    private fun drawAmbient(c: Canvas,w:Float,h:Float) {
        for (i in 0..7) {
            val x = (w * (i + 1) / 9f) + sin(pulse + i) * 12f * density
            val y = (h * ((i * 37 % 83) + 8) / 100f)
            p.color = Color.argb(25,80,210,255); p.style=Paint.Style.FILL
            c.drawCircle(x,y,(3f + (i%3))*density,p)
        }
        p.color=Color.argb(18,90,220,255); p.style=Paint.Style.STROKE; p.strokeWidth=1.2f*density
        c.drawCircle(w*.5f,h*.42f,w*.39f,p); p.style=Paint.Style.FILL
    }

    private fun drawLogo(c:Canvas,w:Float,y:Float) {
        val cx=w*.5f
        rounded(c,cx-34*density,y-35*density,cx+34*density,y+33*density,19*density,Color.rgb(22,55,92),cyan,2*density)
        drawArrow(c,cx,y-1*density,24*density,1,cyan,5*density)
        text(c,"ARROW",cx,y+68*density,35*density,white,Paint.Align.CENTER,true)
        text(c,"ESCAPE  •  PUZZLE",cx,y+92*density,12*density,cyan,Paint.Align.CENTER,true)
    }

    private fun drawHome(c:Canvas,w:Float,h:Float) {
        drawLogo(c,w,h*.19f)
        text(c,"A smart little brain challenge",w*.5f,h*.19f+120*density,14*density,muted,Paint.Align.CENTER)
        val bw=min(w*.68f,310*density); val x=(w-bw)*.5f
        bigButton(c,"PLAY NOW",x,h*.53f,x+bw,h*.53f+55*density,cyan,Color.rgb(7,35,58))
        bigButton(c,"LEVEL SELECT",x,h*.53f+70*density,x+bw,h*.53f+119*density,Color.rgb(48,73,121),white)
        bigButton(c,if(soundOn) "SOUND  ON" else "SOUND  OFF",x,h*.53f+134*density,x+bw,h*.53f+181*density,Color.rgb(35,51,84),white)
        text(c,"COINS  ◆  "+coins,w*.5f,h*.53f+220*density,16*density,gold,Paint.Align.CENTER,true)
        text(c,"Slide every arrow out. Don't let them block each other.",w*.5f,h-25*density,11*density,muted,Paint.Align.CENTER)
    }

    private fun bigButton(c:Canvas,label:String,l:Float,t:Float,r:Float,b:Float,color:Int,txt:Int) {
        rounded(c,l,t+4*density,r,b+4*density,15*density,Color.argb(90,0,0,0))
        rounded(c,l,t,r,b,15*density,color,Color.argb(100,255,255,255),1*density)
        text(c,label,(l+r)*.5f,(t+b)*.5f+6*density,16*density,txt,Paint.Align.CENTER,true)
    }

    private fun drawGame(c:Canvas,w:Float,h:Float) {
        text(c,"ARROW ESCAPE",20*density,34*density,19*density,white,bold=true)
        text(c,"LEVEL "+level,w*.5f,34*density,15*density,cyan,Paint.Align.CENTER,true)
        text(c,"◆ "+coins,w-20*density,34*density,15*density,gold,Paint.Align.RIGHT,true)
        text(c,"♥ ".repeat(hearts)+"♡ ".repeat(3-hearts),w*.5f,59*density,17*density,Color.rgb(255,91,125),Paint.Align.CENTER,true)
        text(c,"SCORE  "+score,20*density,59*density,11*density,gold)
        text(c,message,w*.5f,83*density,10*density,muted,Paint.Align.CENTER)
        val available = min(w-34*density,h*.62f)
        cell = available / gridSize
        val boardSize=cell*gridSize
        boardLeft=(w-boardSize)*.5f
        boardTop=(h-boardSize)*.49f
        rounded(c,boardLeft-5*density,boardTop-5*density,boardLeft+boardSize+5*density,boardTop+boardSize+5*density,15*density,Color.argb(95,8,19,42),Color.argb(70,60,226,238),1*density)
        for(y in 0 until gridSize) for(x in 0 until gridSize) {
            p.color=Color.argb(35,140,180,220); p.style=Paint.Style.FILL
            c.drawCircle(boardLeft+(x+.5f)*cell,boardTop+(y+.5f)*cell,1.4f*density,p)
        }
        for(tile in board) drawTile(c,tile)
        val rowY=boardTop+boardSize+27*density
        text(c,"MOVES  "+moves,rowY*0f+20*density,rowY,12*density,muted,bold=true)
        text(c,"LEFT  "+board.size,w-20*density,rowY,12*density,cyan,Paint.Align.RIGHT,true)
        val btnY=h-66*density
        val gap=9*density; val bw=(w-40*density-2*gap)/3f
        smallButton(c,"HINT",20*density,btnY,20*density+bw,btnY+43*density,gold)
        smallButton(c,"UNDO",20*density+bw+gap,btnY,20*density+2*bw+gap,btnY+43*density,Color.rgb(66,105,168))
        smallButton(c,"RESTART",20*density+2*(bw+gap),btnY,w-20*density,btnY+43*density,Color.rgb(66,105,168))
    }

    private fun drawTile(c:Canvas,tile:ArrowTile) {
        val l=boardLeft+tile.x*cell+4*density
        val t=boardTop+tile.y*cell+4*density
        val r=boardLeft+(tile.x+1)*cell-4*density
        val b=boardTop+(tile.y+1)*cell-4*density
        val base=tileColors[(tile.x+tile.y+level)%tileColors.size]
        if(tile.id==lastHint) {
            p.color=Color.argb(65,255,198,76);p.style=Paint.Style.FILL
            c.drawCircle((l+r)*.5f,(t+b)*.5f,cell*.32f,p)
            p.color=gold;p.style=Paint.Style.STROKE;p.strokeWidth=1.8f*density
            c.drawCircle((l+r)*.5f,(t+b)*.5f,cell*.30f,p);p.style=Paint.Style.FILL
        }
        drawArrow(c,(l+r)*.5f,(t+b)*.5f,cell*.34f,tile.dir,base, max(2.1f*density,cell*.045f))
    }

    private fun drawArrow(c:Canvas,cx:Float,cy:Float,size:Float,dir:Int,color:Int,stroke:Float) {
        val angle=(dir-1)*PI/2.0
        val dx=cos(angle).toFloat(); val dy=sin(angle).toFloat()
        val px=-dy; val py=dx
        val tailX=cx-dx*size*.58f; val tailY=cy-dy*size*.58f
        val headX=cx+dx*size*.62f; val headY=cy+dy*size*.62f
        p.color=color;p.style=Paint.Style.STROKE;p.strokeWidth=stroke;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND
        c.drawLine(tailX,tailY,headX,headY,p)
        path.reset()
        path.moveTo(headX-dx*size*.52f+px*size*.38f,headY-dy*size*.52f+py*size*.38f)
        path.lineTo(headX,headY)
        path.lineTo(headX-dx*size*.52f-px*size*.38f,headY-dy*size*.52f-py*size*.38f)
        c.drawPath(path,p)
        p.style=Paint.Style.FILL
    }

    private fun smallButton(c:Canvas,label:String,l:Float,t:Float,r:Float,b:Float,color:Int) {
        rounded(c,l,t,r,b,12*density,color,Color.argb(80,255,255,255),1*density)
        text(c,label,(l+r)*.5f,(t+b)*.5f+5*density,12*density,white,Paint.Align.CENTER,true)
    }

    private fun drawWin(c:Canvas,w:Float,h:Float) {
        drawLogo(c,w,h*.2f)
        text(c,"PUZZLE COMPLETE!",w*.5f,h*.2f+125*density,23*density,gold,Paint.Align.CENTER,true)
        text(c,"You cleared every arrow.",w*.5f,h*.2f+155*density,14*density,muted,Paint.Align.CENTER)
        rounded(c,w*.2f,h*.48f,w*.8f,h*.66f,18*density,Color.rgb(20,43,77),cyan,1.5f*density)
        text(c,"+10 COINS  •  +10 POINTS",w*.5f,h*.48f+35*density,17*density,gold,Paint.Align.CENTER,true)
        text(c,"MOVES  "+moves,w*.5f,h*.48f+66*density,13*density,white,Paint.Align.CENTER)
        bigButton(c,if(level>=10) "FINISH GAME" else "NEXT LEVEL",w*.22f,h*.72f,w*.78f,h*.72f+50*density,cyan,Color.rgb(7,35,58))
        bigButton(c,"MAIN MENU",w*.22f,h*.72f+64*density,w*.78f,h*.72f+111*density,Color.rgb(48,73,121),white)
    }

    private fun drawGameOver(c:Canvas,w:Float,h:Float) {
        drawLogo(c,w,h*.22f)
        text(c,"NO HEARTS LEFT",w*.5f,h*.22f+125*density,23*density,Color.rgb(255,105,125),Paint.Align.CENTER,true)
        text(c,"You made a wrong move. Try this level again.",w*.5f,h*.22f+155*density,12*density,muted,Paint.Align.CENTER)
        text(c,"LEVEL  "+level+"     SCORE  "+score,w*.5f,h*.49f,15*density,white,Paint.Align.CENTER,true)
        bigButton(c,"RESTART LEVEL",w*.22f,h*.65f,w*.78f,h*.65f+52*density,cyan,Color.rgb(7,35,58))
        bigButton(c,"MAIN MENU",w*.22f,h*.65f+66*density,w*.78f,h*.65f+116*density,Color.rgb(48,73,121),white)
    }

    private fun hit(l:Float,t:Float,r:Float,b:Float,x:Float,y:Float)=x in l..r && y in t..b

    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(event.action!=MotionEvent.ACTION_UP) return true
        val x=event.x; val y=event.y; val w=width.toFloat(); val h=height.toFloat()
        when(screen) {
            0 -> {
                val x0=w*.16f; val x1=w*.84f
                if(hit(x0,h*.53f,x1,h*.53f+60*density,x,y)) { screen=1;generateLevel() }
                else if(hit(x0,h*.53f+68*density,x1,h*.53f+126*density,x,y)) { screen=1;level=1;generateLevel() }
                else if(hit(x0,h*.53f+130*density,x1,h*.53f+190*density,x,y)) { soundOn=!soundOn;save() }
            }
            1 -> {
                val boardSize=cell*gridSize
                val btnY=h-66*density
                val gap=9*density; val bw=(w-40*density-2*gap)/3f
                if(y>=btnY && y<=btnY+50*density) {
                    when {
                        x<20*density+bw -> {
                            if(hints>0) {
                                val ready=board.firstOrNull { canExit(it,board) }
                                if(ready!=null) { lastHint=ready.id;hints--;message="Hint: this arrow can escape" }
                                else message="No safe move found"
                            } else if(coins>=15) {
                                coins-=15
                                val ready=board.firstOrNull { canExit(it,board) }
                                if(ready!=null) { lastHint=ready.id;message="Hint unlocked with 15 coins" }
                                save()
                            } else message="Not enough coins for a hint"
                        }
                        x<20*density+2*bw+gap -> {
                            if(history.isNotEmpty()) { board=history.removeLast().toMutableList();moves=(moves-1).coerceAtLeast(0);message="Last move undone";levelWon=false }
                            else message="Nothing to undo"
                        }
                        else -> generateLevel()
                    }
                } else if(x in boardLeft..(boardLeft+boardSize) && y in boardTop..(boardTop+boardSize)) {
                    val gx=((x-boardLeft)/cell).toInt(); val gy=((y-boardTop)/cell).toInt()
                    val tile=board.firstOrNull { it.x==gx && it.y==gy }
                    if(tile!=null) {
                        if(canExit(tile,board)) removeTile(tile)
                        else {
                            hearts--
                            lastHint=-1
                            message="Wrong arrow! -1 heart"
                            if(hearts<=0) screen=3
                        }
                    }
                }
            }
            2 -> {
                if(y>h*.70f && y<h*.72f+58*density) {
                    if(level>=10) { level=1;screen=0;save() }
                    else { level++;save();screen=1;generateLevel() }
                } else if(y>h*.72f+60*density) { screen=0;save() }
            }
            3 -> {
                if(y>h*.64f && y<h*.65f+58*density) { screen=1;generateLevel() }
                else if(y>h*.65f+60*density) { screen=0;save() }
            }
        }
        invalidate()
        return true
    }
}
