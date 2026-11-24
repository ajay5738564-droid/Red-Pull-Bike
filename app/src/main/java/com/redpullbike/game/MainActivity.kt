package com.redpullbike.game

import android.os.Bundle
import android.media.AudioManager
import android.media.ToneGenerator
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
private data class PuzzleLevel(val id:Int,val cols:Int,val rows:Int,val paths:List<ArrowPath>,val difficulty:String,val artName:String)

private object Levels {
    private val names = listOf("APPLE","LION","DOG","CAT","BUTTERFLY","FISH","HEART","TURTLE","OWL","ROCKET","DINO","FLOWER")
    // Each row is the left/right extent of the artwork silhouette on a 15x15 canvas.
    private val silhouettes = listOf(
        listOf(6 to 8,4 to 10,2 to 12,1 to 13,0 to 14,0 to 14,1 to 13,2 to 12,3 to 11,4 to 10,5 to 9,6 to 8,6 to 8,7 to 7,7 to 7), // apple
        listOf(4 to 10,2 to 12,0 to 14,1 to 13,0 to 14,1 to 13,2 to 12,1 to 13,0 to 14,1 to 13,2 to 12,3 to 11,4 to 10,5 to 9,6 to 8), // lion mane
        listOf(1 to 5,0 to 7,1 to 9,2 to 12,3 to 13,4 to 13,4 to 12,3 to 11,2 to 10,2 to 9,3 to 8,4 to 7,5 to 8,6 to 9,7 to 10), // dog
        listOf(2 to 4,1 to 5,0 to 6,1 to 13,2 to 12,3 to 11,4 to 10,4 to 10,3 to 11,2 to 12,1 to 13,2 to 12,4 to 10,5 to 9,6 to 8), // cat
        listOf(6 to 8,4 to 10,2 to 12,0 to 14,1 to 13,3 to 11,5 to 9,6 to 8,5 to 9,3 to 11,1 to 13,0 to 14,2 to 12,4 to 10,6 to 8), // butterfly
        listOf(6 to 8,4 to 10,2 to 12,0 to 14,1 to 13,2 to 12,3 to 11,4 to 10,3 to 11,2 to 12,1 to 13,0 to 14,2 to 12,4 to 10,6 to 8), // fish
        listOf(7 to 7,5 to 9,3 to 11,1 to 13,0 to 14,0 to 14,1 to 13,2 to 12,3 to 11,4 to 10,5 to 9,6 to 8,7 to 7,8 to 8,8 to 8), // heart
        listOf(4 to 10,2 to 12,1 to 13,0 to 14,1 to 13,2 to 12,3 to 11,2 to 12,1 to 13,0 to 14,1 to 13,2 to 12,4 to 10,5 to 9,6 to 8), // turtle
        listOf(5 to 9,3 to 11,1 to 13,0 to 14,1 to 13,2 to 12,3 to 11,4 to 10,3 to 11,2 to 12,1 to 13,2 to 12,3 to 11,4 to 10,5 to 9), // owl
        listOf(7 to 7,6 to 8,5 to 9,4 to 10,3 to 11,2 to 12,1 to 13,0 to 14,1 to 13,2 to 12,3 to 11,4 to 10,5 to 9,6 to 8,7 to 7), // rocket
        listOf(2 to 12,1 to 13,0 to 14,1 to 13,2 to 12,3 to 11,2 to 12,1 to 13,0 to 14,1 to 13,2 to 12,3 to 11,4 to 10,5 to 9,6 to 8), // dino
        listOf(7 to 7,6 to 8,4 to 10,2 to 12,0 to 14,1 to 13,3 to 11,5 to 9,6 to 8,5 to 9,3 to 11,1 to 13,0 to 14,2 to 12,6 to 8) // flower
    )

    private val cache = HashMap<Int,PuzzleLevel>()
    fun get(id:Int):PuzzleLevel = cache.getOrPut(id.coerceIn(1,500)) { make(id.coerceIn(1,500)) }

    private fun make(id:Int):PuzzleLevel {
        val artIndex = ((id-1)*7 + (id-1)/3) % silhouettes.size
        val baseArt = silhouettes[artIndex]
        val variant=(id-1)/silhouettes.size
        val art = (if(variant%3==1) baseArt.reversed() else baseArt).mapIndexed { row, span ->
            val mirror = variant%2==1
            val wobble = if(variant==0) 0 else ((row*variant+id)%3)-1
            val left = (if(mirror) 14-span.second else span.first) + wobble
            val right = (if(mirror) 14-span.first else span.second) - wobble
            min(left,right).coerceIn(0,10) to max(left,right).coerceIn(4,14)
        }
        val tier = ((id-1)/50).coerceAtMost(9)
        val cols = 18 + tier
        val rows = 18 + tier
        val sx=(cols-2)/14f
        val sy=(rows-2)/14f
        val paths=ArrayList<ArrowPath>()
        var nextId=0
        art.forEachIndexed { row, span ->
            val left=1f+span.first*sx
            val right=1f+span.second*sx
            val y=1f+row*sy
            val width=right-left
            val chunk=(2.55f - tier*.065f + ((id+row)%3)*.07f).coerceAtLeast(1.82f)
            val pieces=max(1,ceil(width/chunk).toInt())
            val gap=.16f
            val usable=width-gap*(pieces-1)
            val part=usable/pieces
            val pointsForRow=(0 until pieces).map { index ->
                val x0=left+index*(part+gap)
                val x1=x0+part
                val toRight=((row+variant)%2==0)
                val amp=(.30f + ((id+row+index)%3)*.055f)*sy
                val turns=3+((id*3+row+index)%3)
                val points=ArrayList<V>()
                var currentY=y+(if((id+row+index)%2==0) -amp else amp)
                if(toRight) {
                    points.add(V(x0,currentY))
                    for(k in 0 until turns) {
                        val xx=x0+part*(k+1)/(turns+1f)
                        points.add(V(xx,currentY))
                        currentY=y+(if((k+row+id+index)%2==0) amp else -amp)
                        points.add(V(xx,currentY))
                    }
                    points.add(V(x1,currentY))
                } else {
                    points.add(V(x1,currentY))
                    for(k in 0 until turns) {
                        val xx=x1-part*(k+1)/(turns+1f)
                        points.add(V(xx,currentY))
                        currentY=y+(if((k+row+id+index)%2==0) amp else -amp)
                        points.add(V(xx,currentY))
                    }
                    points.add(V(x0,currentY))
                }
                val exit=points.last()
                val dir=if(toRight) V(1f,0f) else V(-1f,0f)
                ArrowPath(nextId++,points,exit,dir)
            }
            paths.addAll(pointsForRow)
        }
        val diff=when(id){in 1..10->"Hard";in 11..50->"Very Hard";in 51..150->"Expert";in 151..300->"Master";else->"Extreme"}
        return PuzzleLevel(id,cols,rows,paths,diff,names[artIndex])
    }
}

@Composable
private fun ArrowEscapeApp() {
    val context = LocalContext.current
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 45) }
    DisposableEffect(Unit) { onDispose { tone.release() } }
    val prefs = remember { context.getSharedPreferences("arrow_escape_v2", 0) }
    var highest by remember { mutableIntStateOf(prefs.getInt("highest",1).coerceIn(1,500)) }
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
    var bounceId by remember { mutableIntStateOf(-1) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var completed by remember { mutableIntStateOf(prefs.getInt("completed",0).coerceIn(0,500)) }
    val puzzle = remember(level) { Levels.get(level) }
    LaunchedEffect(bounceId) { if(bounceId>=0) { delay(280); bounceId=-1 } }
    LaunchedEffect(animating) { val escaped=animating; if(escaped>=0) { delay(720); remaining=remaining-escaped; hintId=-1; animating=-1 } }
    fun start(id:Int) {
        level=id.coerceIn(1,500); remaining=Levels.get(level).paths.map { it.id }.toSet()
        lives=3; hints=if(level<=10) 2 else if(level<=30) 1 else 0
        hintId=-1; message=""; animating=-1; pan=Offset.Zero; zoom=1f; page="game"
    }
    when(page) {
        "home" -> HomeScreen(onPlay={start(highest)}, onLevels={page="levels"}, onSettings={page="settings"})
        "levels" -> LevelSelect(highest,completed,onBack={page="home"},onSelect={start(it)})
        "settings" -> SettingsScreen(showGrid,sound,haptics,{showGrid=it;prefs.edit().putBoolean("grid",it).apply()},{sound=it;prefs.edit().putBoolean("sound",it).apply()},{haptics=it;prefs.edit().putBoolean("haptics",it).apply()},{page="home"})
        "game" -> {
            val solved = remaining.isEmpty()
            LaunchedEffect(solved) { if(solved) { if(sound) tone.startTone(ToneGenerator.TONE_PROP_ACK,120); delay(500); page="complete"; completed=maxOf(completed,level); highest=maxOf(highest,(level+1).coerceAtMost(500)); prefs.edit().putInt("completed",completed).putInt("highest",highest).apply() } }
            GameScreen(puzzle,remaining,lives,hints,hintId,showGrid,message,zoom,pan,bounceId,animating,
                onZoom={zoom=(zoom*it).coerceIn(.65f,2.3f)},
                onPan={pan+=it},
                onArrow={id ->
                    if(animating!=-1 || page!="game") return@GameScreen
                    val arrow=puzzle.paths.firstOrNull { it.id==id } ?: return@GameScreen
                    val isFree = isFree(arrow,puzzle.paths.filter { it.id in remaining && it.id!=id },puzzle.cols,puzzle.rows)
                    if(isFree) { if(sound) tone.startTone(ToneGenerator.TONE_PROP_BEEP,55); animating=id; message="" }
                    else { if(sound) tone.startTone(ToneGenerator.TONE_PROP_NACK,100); lives--; bounceId=id; message="Blocked! The arrow bounced back."; if(lives<=0) page="failed" }
                },
                onHint={ if(hints>0) { val candidate=puzzle.paths.firstOrNull { it.id in remaining && isFree(it,puzzle.paths.filter { p->p.id in remaining && p.id!=it.id },puzzle.cols,puzzle.rows) }; if(candidate!=null){hintId=candidate.id;hints--;message="This arrow has a free path."} else message="No free arrow found." } else message="No hints left." },
                onGrid={showGrid=!showGrid},onRestart={start(level)},onBack={page="levels"}
            )
        }
        "complete" -> CompleteScreen(level,onNext={if(level<500) start(level+1) else page="home"},onHome={page="home"})
        "failed" -> ResultScreen("Out of Lives","Think carefully and try again.", "RESTART LEVEL",{start(level)},{page="levels"})
    }
}

private fun isFree(a:ArrowPath,others:List<ArrowPath>,cols:Int,rows:Int):Boolean {
    val head=a.exit; val d=a.direction
    // Direct orthogonal ray/segment checks replace costly point-by-point sampling.
    for(o in others) for(j in 0 until o.points.lastIndex) {
        val p=o.points[j]; val q=o.points[j+1]
        if(abs(d.x)>.5f) {
            if(abs(p.y-q.y)<.0001f && abs(p.y-head.y)<.18f) {
                val lo=min(p.x,q.x); val hi=max(p.x,q.x)
                if(if(d.x>0f) hi>head.x+.12f && lo<=cols else lo<head.x-.12f && hi>=0f) return false
            } else if(abs(p.x-q.x)<.0001f) {
                val lo=min(p.y,q.y); val hi=max(p.y,q.y)
                if(head.y>=lo-.12f && head.y<=hi+.12f && (if(d.x>0f) p.x>head.x+.12f && p.x<=cols else p.x<head.x-.12f && p.x>=0f)) return false
            }
        } else {
            if(abs(p.x-q.x)<.0001f && abs(p.x-head.x)<.18f) {
                val lo=min(p.y,q.y); val hi=max(p.y,q.y)
                if(if(d.y>0f) hi>head.y+.12f && lo<=rows else lo<head.y-.12f && hi>=0f) return false
            } else if(abs(p.y-q.y)<.0001f) {
                val lo=min(p.x,q.x); val hi=max(p.x,q.x)
                if(head.x>=lo-.12f && head.x<=hi+.12f && (if(d.y>0f) p.y>head.y+.12f && p.y<=rows else p.y<head.y-.12f && p.y>=0f)) return false
            }
        }
    }
    return true
}
private fun pointNearSegment(p:V,a:V,b:V,r:Float):Boolean {
    val dx=b.x-a.x; val dy=b.y-a.y
    val den=dx*dx+dy*dy
    val t=if(den==0f) 0f else (((p.x-a.x)*dx+(p.y-a.y)*dy)/den).coerceIn(0f,1f)
    val ex=p.x-(a.x+t*dx); val ey=p.y-(a.y+t*dy)
    return ex*ex+ey*ey<r*r
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
            items(500) { i -> val n=i+1; val unlocked=n<=highest
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
@Composable private fun GameScreen(level:PuzzleLevel,remaining:Set<Int>,lives:Int,hints:Int,hintId:Int,grid:Boolean,message:String,zoom:Float,pan:Offset,bounceId:Int,escapingId:Int,onZoom:(Float)->Unit,onPan:(Offset)->Unit,onArrow:(Int)->Unit,onHint:()->Unit,onGrid:()->Unit,onRestart:()->Unit,onBack:()->Unit) {
    val bounceProgress by animateFloatAsState(targetValue=if(bounceId>=0) 1f else 0f, animationSpec=tween(230, easing=FastOutSlowInEasing), label="arrowBounce")
    val escapeProgress by animateFloatAsState(targetValue=if(escapingId>=0) 1f else 0f, animationSpec=tween(680, easing=LinearOutSlowInEasing), label="arrowEscape")
    Column(Modifier.fillMaxSize().background(Color.White).padding(horizontal=18.dp, vertical=8.dp)) {
        Text("Tap Away\nArrows",modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center,fontSize=30.sp,lineHeight=34.sp,fontWeight=FontWeight.ExtraBold,color=Navy)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("‹",fontSize=32.sp,color=Blue,modifier=Modifier.clickable{onBack()})
            Column(horizontalAlignment=Alignment.CenterHorizontally){Text("Level ${level.id}",fontSize=18.sp,color=Color.Black,fontWeight=FontWeight.SemiBold);Text("${level.artName}  •  ${level.paths.count{it.id in remaining}} arrows",fontSize=12.sp,color=Color.Gray)}
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
                    var hitId=-1
                    for(path in level.paths) {
                        if(path.id !in remaining) continue
                        for(j in 0 until path.points.lastIndex) {
                            if(pointNearSegment(p,path.points[j],path.points[j+1],.23f)) { hitId=path.id; break }
                        }
                        if(hitId>=0) break
                    }
                    if(hitId>=0) onArrow(hitId)
                }
            }) {
                val side=min(size.width/level.cols,size.height/level.rows)*zoom
                val ox=(size.width-side*level.cols)/2f+pan.x
                val oy=(size.height-side*level.rows)/2f+pan.y
                if(grid) for(x in 0..level.cols) for(y in 0..level.rows) drawCircle(Color(0xFFDCE2E8),1.35.dp.toPx(),Offset(ox+x*side,oy+y*side))
                level.paths.filter{it.id in remaining}.forEach { arrow ->
                    val bounceShift=if(arrow.id==bounceId) sin(bounceProgress*PI).toFloat()*side*.48f else 0f
                    val isEscaping=arrow.id==escapingId
                    val escapeCells=when {
                        arrow.direction.x>0f -> level.cols-arrow.exit.x+1.5f
                        arrow.direction.x<0f -> arrow.exit.x+1.5f
                        arrow.direction.y>0f -> level.rows-arrow.exit.y+1.5f
                        else -> arrow.exit.y+1.5f
                    }
                    val morph=if(isEscaping) (escapeProgress/.42f).coerceIn(0f,1f) else 0f
                    val travel=if(isEscaping) ((escapeProgress-.42f)/.58f).coerceIn(0f,1f) else 0f
                    val shift=bounceShift+travel*escapeCells*side
                    val pts=if(isEscaping) {
                        var travelled=0f
                        val straight=ArrayList<V>(arrow.points.size)
                        for(i in arrow.points.indices) {
                            if(i>0) travelled+=hypot(arrow.points[i].x-arrow.points[i-1].x,arrow.points[i].y-arrow.points[i-1].y)
                            straight.add(V(arrow.exit.x-arrow.direction.x*travelled,arrow.exit.y-arrow.direction.y*travelled))
                        }
                        arrow.points.indices.map { i ->
                            val p=arrow.points[i]; val q=straight[i]
                            val x=p.x+(q.x-p.x)*morph
                            val y=p.y+(q.y-p.y)*morph
                            Offset(ox+x*side+arrow.direction.x*shift,oy+y*side+arrow.direction.y*shift)
                        }
                    } else arrow.points.map { Offset(ox+it.x*side+arrow.direction.x*shift,oy+it.y*side+arrow.direction.y*shift) }
                    val col=if(arrow.id==hintId) Blue else Navy
                    val path=Path().apply { pts.firstOrNull()?.let{moveTo(it.x,it.y)};pts.drop(1).forEach{lineTo(it.x,it.y)} }
                    drawPath(path,col,style=Stroke(width=max(.85.dp.toPx(),side*.018f),cap=StrokeCap.Round,join=PathEffectCorner))
                    val end=pts.last()
                    val angle=atan2(arrow.direction.y,arrow.direction.x)
                    val len=side*.105f; val wing=side*.052f
                    val tip=Offset(end.x+cos(angle)*len,end.y+sin(angle)*len)
                    val left=Offset(tip.x-cos(angle)*len*.8f+sin(angle)*wing,tip.y-sin(angle)*len*.8f-cos(angle)*wing)
                    val right=Offset(tip.x-cos(angle)*len*.8f-sin(angle)*wing,tip.y-sin(angle)*len*.8f+cos(angle)*wing)
                    drawLine(col,end,tip,strokeWidth=max(1.dp.toPx(),side*.028f),cap=StrokeCap.Round)
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
        Spacer(Modifier.height(34.dp)); Button(onClick=onNext,modifier=Modifier.fillMaxWidth().height(56.dp),colors=ButtonDefaults.buttonColors(containerColor=Color.White)){Text(if(level==500) "Finish" else "Next Level",color=Blue)}
        TextButton(onClick=onHome){Text("Main Menu",color=Color.White)}
    }
}
@Composable private fun ResultScreen(title:String,sub:String,button:String,onRetry:()->Unit,onBack:()->Unit) {
    Column(Modifier.fillMaxSize().background(Color.White).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Text(title,fontSize=30.sp,fontWeight=FontWeight.Bold,color=Navy); Text(sub,color=Color.Gray); Spacer(Modifier.height(28.dp)); PrimaryButton(button,onRetry); TextButton(onClick=onBack){Text("Level Select")}
    }
}
