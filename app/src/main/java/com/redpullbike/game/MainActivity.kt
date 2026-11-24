package com.redpullbike.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

private val Navy = Color(0xFF1C244C)
private val Blue = Color(0xFF168BDA)
private val Pale = Color(0xFFF2F6FA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        setContent { MaterialTheme { ArrowEscapeApp() } }
    }
}

private data class V(val x: Float, val y: Float)
private data class ArrowPath(val id: Int, val points: List<V>, val exit: V, val direction: V)
private data class PuzzleLevel(val id: Int, val cols: Int, val rows: Int, val paths: List<ArrowPath>, val difficulty: String)

private object Levels {
    private val cache = (1..50).map { make(it) }
    fun get(id: Int) = cache[(id - 1).coerceIn(0,49)]
    private fun make(id: Int): PuzzleLevel {
        val tier = when(id) { in 1..5 -> 0; in 6..10 -> 1; in 11..20 -> 2; in 21..30 -> 3; in 31..40 -> 4; else -> 5 }
        val cols = listOf(5,6,7,8,9,10)[tier] + if (id % 3 == 0) 1 else 0
        val rows = listOf(5,6,7,8,9,10)[tier] + if (id % 4 == 0) 1 else 0
        val count = when(id) { in 1..5 -> 6 + id; in 6..10 -> 9 + id/2; in 11..20 -> 13 + id/2; in 21..30 -> 18 + id/2; in 31..40 -> 25 + id/2; else -> 34 + id/2 }.coerceAtMost(65)
        val rng = Random(id * 9109)
        val used = HashSet<Pair<Int,Int>>()
        val paths = ArrayList<ArrowPath>()
        // Deterministic original maze-like paths. Each path ends in an outward-facing arrow.
        repeat(count) { n ->
            var x = rng.nextInt(cols); var y = rng.nextInt(rows)
            var tries = 0
            while ((x to y) in used && tries++ < cols*rows) { x = rng.nextInt(cols); y = rng.nextInt(rows) }
            used.add(x to y)
            val edge = (n + id + rng.nextInt(4)) % 4
            val end = when(edge) {
                0 -> V(x + .5f, 0f)
                1 -> V(cols.toFloat(), y + .5f)
                2 -> V(x + .5f, rows.toFloat())
                else -> V(0f, y + .5f)
            }
            val start = V(x + .5f, y + .5f)
            val elbow = when(edge) {
                0,2 -> V(start.x, (start.y + end.y)/2f)
                else -> V((start.x + end.x)/2f, start.y)
            }
            val pts = if (id <= 5 || n % 3 == 0) listOf(start,end) else listOf(start, elbow, V(elbow.x + if(n%2==0) .25f else -.25f, elbow.y), end)
            val dir = when(edge) { 0 -> V(0f,-1f); 1 -> V(1f,0f); 2 -> V(0f,1f); else -> V(-1f,0f) }
            paths += ArrowPath(n, pts, end, dir)
        }
        return PuzzleLevel(id, cols, rows, paths, when(id) { in 1..10 -> "Easy"; in 11..30 -> "Normal"; in 31..47 -> "Hard"; else -> "Expert" })
    }
}

@Composable
private fun ArrowEscapeApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("arrow_escape_v2", 0) }
    var highest by remember { mutableIntStateOf(prefs.getInt("highest",1).coerceIn(1,50)) }
    var level by remember { mutableIntStateOf(highest) }
    var page by remember { mutableStateOf("home") }
    var remaining by remember { mutableStateOf(emptySet<Int>()) }
    var lives by remember { mutableIntStateOf(3) }
    var hints by remember { mutableIntStateOf(2) }
    var hintId by remember { mutableIntStateOf(-1) }
    var showGrid by remember { mutableStateOf(true) }
    var sound by remember { mutableStateOf(true) }
    var haptics by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var animating by remember { mutableIntStateOf(-1) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var completed by remember { mutableIntStateOf(prefs.getInt("completed",0)) }
    val puzzle = remember(level) { Levels.get(level) }
    fun start(id:Int) {
        level=id.coerceIn(1,50); remaining=Levels.get(level).paths.map { it.id }.toSet()
        lives=3; hints=if(level<=10) 2 else if(level<=30) 1 else 0
        hintId=-1; message=""; animating=-1; pan=Offset.Zero; zoom=1f; page="game"
    }
    when(page) {
        "home" -> HomeScreen(onPlay={start(highest)}, onLevels={page="levels"}, onSettings={page="settings"})
        "levels" -> LevelSelect(highest,completed,onBack={page="home"},onSelect={start(it)})
        "settings" -> SettingsScreen(showGrid,sound,haptics,{showGrid=it;prefs.edit().putBoolean("grid",it).apply()},{sound=it;prefs.edit().putBoolean("sound",it).apply()},{haptics=it;prefs.edit().putBoolean("haptics",it).apply()},{page="home"})
        "game" -> {
            val solved = remaining.isEmpty()
            LaunchedEffect(solved) { if(solved) { delay(500); page="complete"; completed=maxOf(completed,level); highest=maxOf(highest,(level+1).coerceAtMost(50)); prefs.edit().putInt("completed",completed).putInt("highest",highest).apply() } }
            GameScreen(puzzle,remaining,lives,hints,hintId,showGrid,message,zoom,pan,
                onZoom={zoom=(zoom*it).coerceIn(.65f,2.3f)},
                onPan={pan+=it},
                onArrow={id ->
                    if(animating!=-1 || page!="game") return@GameScreen
                    val arrow=puzzle.paths.firstOrNull { it.id==id } ?: return@GameScreen
                    val isFree = isFree(arrow,puzzle.paths.filter { it.id in remaining && it.id!=id },puzzle.cols,puzzle.rows)
                    if(isFree) { animating=id; message=""; remaining=remaining-id; hintId=-1; animating=-1 }
                    else { lives--; message="Blocked! The arrow bounced back."; if(lives<=0) page="failed" }
                },
                onHint={ if(hints>0) { val candidate=puzzle.paths.firstOrNull { it.id in remaining && isFree(it,puzzle.paths.filter { p->p.id in remaining && p.id!=it.id },puzzle.cols,puzzle.rows) }; if(candidate!=null){hintId=candidate.id;hints--;message="This arrow has a free path."} else message="No free arrow found." } else message="No hints left." },
                onGrid={showGrid=!showGrid},onRestart={start(level)},onBack={page="levels"}
            )
        }
        "complete" -> CompleteScreen(level,onNext={if(level<50) start(level+1) else page="home"},onHome={page="home"})
        "failed" -> ResultScreen("Out of Lives","Think carefully and try again.", "RESTART LEVEL",{start(level)},{page="levels"})
    }
}

private fun isFree(a:ArrowPath,others:List<ArrowPath>,cols:Int,rows:Int):Boolean {
    val head=a.exit
    val d=a.direction
    val limit= max(cols,rows)*2
    for(i in 1..limit) {
        val q=V(head.x+d.x*i*.18f,head.y+d.y*i*.18f)
        if(q.x<0f||q.y<0f||q.x>cols||q.y>rows) return true
        for(o in others) for(j in 0 until o.points.lastIndex) {
            if(pointNearSegment(q,o.points[j],o.points[j+1],.12f)) return false
        }
    }
    return true
}
private fun pointNearSegment(p:V,a:V,b:V,r:Float):Boolean {
    val dx=b.x-a.x; val dy=b.y-a.y
    val den=dx*dx+dy*dy
    val t=if(den==0f) 0f else (((p.x-a.x)*dx+(p.y-a.y)*dy)/den).coerceIn(0f,1f)
    return hypot(p.x-(a.x+t*dx),p.y-(a.y+t*dy))<r
}

@Composable
private fun HomeScreen(onPlay:()->Unit,onLevels:()->Unit,onSettings:()->Unit) {
    Column(Modifier.fillMaxSize().background(Color.White).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Text("ARROW",fontSize=38.sp,fontWeight=FontWeight.Black,color=Navy)
        Text("ESCAPE",fontSize=38.sp,fontWeight=FontWeight.Black,color=Blue)
        Text("A calm logic puzzle",color=Color.Gray,modifier=Modifier.padding(bottom=42.dp))
        PrimaryButton("PLAY",onPlay); Spacer(Modifier.height(12.dp)); SecondaryButton("LEVELS",onLevels); Spacer(Modifier.height(12.dp)); SecondaryButton("SETTINGS",onSettings)
    }
}
@Composable private fun PrimaryButton(label:String,click:()->Unit) { Button(onClick=click,modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=Blue)){Text(label,fontWeight=FontWeight.Bold)} }
@Composable private fun SecondaryButton(label:String,click:()->Unit) { OutlinedButton(onClick=click,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(16.dp)){Text(label,color=Navy)} }
@Composable private fun LevelSelect(highest:Int,completed:Int,onBack:()->Unit,onSelect:(Int)->Unit) {
    Column(Modifier.fillMaxSize().background(Color.White).padding(18.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){Text("‹",fontSize=34.sp,color=Blue,modifier=Modifier.clickable{onBack()}); Text("  SELECT LEVEL",fontWeight=FontWeight.Bold,fontSize=22.sp,color=Navy)}
        LazyVerticalGrid(GridCells.Fixed(5),contentPadding=PaddingValues(vertical=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(50) { i -> val n=i+1; val unlocked=n<=highest
                Surface(modifier=Modifier.aspectRatio(1f).clickable(enabled=unlocked){onSelect(n)},shape=RoundedCornerShape(12.dp),color=if(n<=completed) Color(0xFFE4F4FF) else if(unlocked) Pale else Color(0xFFF7F7F7)) {
                    Box(contentAlignment=Alignment.Center){Text(if(!unlocked) "🔒" else if(n<=completed) "$n ✓" else "$n",color=if(unlocked) Navy else Color.LightGray,fontWeight=FontWeight.Bold)}
                }
            }
        }
    }
}
@Composable private fun SettingsScreen(grid:Boolean,sound:Boolean,haptics:Boolean,onGrid:(Boolean)->Unit,onSound:(Boolean)->Unit,onHaptics:(Boolean)->Unit,onBack:()->Unit) {
    Column(Modifier.fillMaxSize().background(Color.White).padding(22.dp)) {
        Text("‹  SETTINGS",fontSize=24.sp,color=Navy,fontWeight=FontWeight.Bold,modifier=Modifier.clickable{onBack()})
        Spacer(Modifier.height(24.dp))
        SettingRow("Show grid",grid,onGrid); SettingRow("Sound",sound,onSound); SettingRow("Haptics",haptics,onHaptics)
        Spacer(Modifier.height(20.dp)); Text("Arrow Escape Puzzle • Offline game",color=Color.Gray)
    }
}
@Composable private fun SettingRow(label:String,value:Boolean,onChange:(Boolean)->Unit) { Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(label,color=Navy);Switch(value,onChange)} }
@Composable private fun GameScreen(level:PuzzleLevel,remaining:Set<Int>,lives:Int,hints:Int,hintId:Int,grid:Boolean,message:String,zoom:Float,pan:Offset,onZoom:(Float)->Unit,onPan:(Offset)->Unit,onArrow:(Int)->Unit,onHint:()->Unit,onGrid:()->Unit,onRestart:()->Unit,onBack:()->Unit) {
    Column(Modifier.fillMaxSize().background(Color.White).padding(horizontal=18.dp, vertical=8.dp)) {
        Text("Tap Away\nArrows",modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center,fontSize=30.sp,lineHeight=34.sp,fontWeight=FontWeight.ExtraBold,color=Navy)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("‹",fontSize=32.sp,color=Blue,modifier=Modifier.clickable{onBack()})
            Column(horizontalAlignment=Alignment.CenterHorizontally){Text("Level ${level.id}",fontSize=18.sp,color=Color.Black,fontWeight=FontWeight.SemiBold);Text("${level.paths.count{it.id in remaining}} arrows",fontSize=12.sp,color=Color.Gray)}
            Text(level.difficulty,fontSize=13.sp,color=Navy,modifier=Modifier.background(Pale,RoundedCornerShape(12.dp)).padding(10.dp))
        }
        Text("♥  ".repeat(lives)+"♡  ".repeat(3-lives),modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center,fontSize=25.sp,color=Color(0xFFEF3048))
        if(message.isNotBlank()) Text(message,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center,color=Blue,fontSize=12.sp)
        Box(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
            detectTransformGestures { _,panChange,zoomChange,_ -> onPan(panChange); onZoom(zoomChange) }
        }) {
            Canvas(Modifier.fillMaxSize().pointerInput(level,remaining,zoom,pan) {
                detectTapGestures { pos ->
                    val side=min(size.width/level.cols,size.height/level.rows)*zoom
                    val ox=(size.width-side*level.cols)/2f+pan.x
                    val oy=(size.height-side*level.rows)/2f+pan.y
                    val p=V((pos.x-ox)/side,(pos.y-oy)/side)
                    val hit=level.paths.filter{it.id in remaining}.firstOrNull { path ->
                        path.points.zipWithNext().any { (a,b)->pointNearSegment(p,a,b,.34f) }
                    }
                    hit?.let{onArrow(it.id)}
                }
            }) {
                val side=min(size.width/level.cols,size.height/level.rows)*zoom
                val ox=(size.width-side*level.cols)/2f+pan.x
                val oy=(size.height-side*level.rows)/2f+pan.y
                if(grid) for(x in 0..level.cols) for(y in 0..level.rows) drawCircle(Color(0xFFDCE2E8),2.dp.toPx(),Offset(ox+x*side,oy+y*side))
                level.paths.filter{it.id in remaining}.forEach { arrow ->
                    val pts=arrow.points.map{Offset(ox+it.x*side,oy+it.y*side)}
                    val col=if(arrow.id==hintId) Blue else Navy
                    val path=Path().apply { pts.firstOrNull()?.let{moveTo(it.x,it.y)};pts.drop(1).forEach{lineTo(it.x,it.y)} }
                    drawPath(path,col,style=Stroke(width=max(3.dp.toPx(),side*.07f),cap=StrokeCap.Round,join=PathEffectCorner))
                    val end=pts.last()
                    val angle=atan2(arrow.direction.y,arrow.direction.x)
                    val len=side*.24f; val wing=side*.13f
                    val tip=Offset(end.x+cos(angle)*len,end.y+sin(angle)*len)
                    val left=Offset(tip.x-cos(angle)*len*.8f+sin(angle)*wing,tip.y-sin(angle)*len*.8f-cos(angle)*wing)
                    val right=Offset(tip.x-cos(angle)*len*.8f-sin(angle)*wing,tip.y-sin(angle)*len*.8f+cos(angle)*wing)
                    drawLine(col,end,tip,strokeWidth=max(3.dp.toPx(),side*.07f),cap=StrokeCap.Round)
                    val head=Path().apply{moveTo(tip.x,tip.y);lineTo(left.x,left.y);lineTo(right.x,right.y);close()}
                    drawPath(head,col)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom=14.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
            AssistButton("💡  $hints",onHint); AssistButton("▦",onGrid); AssistButton("↻",onRestart)
        }
    }
}
private val PathEffectCorner get() = StrokeJoin.Round
@Composable private fun AssistButton(label:String,onClick:()->Unit) { Surface(onClick=onClick,shape=CircleShape,color=Pale,modifier=Modifier.size(66.dp)){Box(contentAlignment=Alignment.Center){Text(label,color=Blue,fontSize=17.sp,fontWeight=FontWeight.Bold)}} }
@Composable private fun CompleteScreen(level:Int,onNext:()->Unit,onHome:()->Unit) {
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF168BDA),Color(0xFF0758A8)))).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Text("✦  ✧  ✦",fontSize=38.sp,color=Color.White); Text("Level Complete!",fontSize=30.sp,fontWeight=FontWeight.ExtraBold,color=Color.White); Text("Great job! You cleared the board.",color=Color.White)
        Spacer(Modifier.height(34.dp)); Button(onClick=onNext,modifier=Modifier.fillMaxWidth().height(56.dp),colors=ButtonDefaults.buttonColors(containerColor=Color.White)){Text(if(level==50) "Finish" else "Next Level",color=Blue)}
        TextButton(onClick=onHome){Text("Main Menu",color=Color.White)}
    }
}
@Composable private fun ResultScreen(title:String,sub:String,button:String,onRetry:()->Unit,onBack:()->Unit) {
    Column(Modifier.fillMaxSize().background(Color.White).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Text(title,fontSize=30.sp,fontWeight=FontWeight.Bold,color=Navy); Text(sub,color=Color.Gray); Spacer(Modifier.height(28.dp)); PrimaryButton(button,onRetry); TextButton(onClick=onBack){Text("Level Select")}
    }
}
