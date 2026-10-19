package com.localcash.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import com.localcash.app.model.Movimiento;

import java.util.ArrayList;
import java.util.List;

/** Mapa local sin conexión: dibuja y permite desplazar los puntos GPS guardados. */
public final class LocationMapView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Movimiento> movimientos = new ArrayList<>();
    private float offsetX, offsetY, downX, downY;

    public LocationMapView(Context context) { super(context); paint.setStrokeWidth(2f); }
    public void setMovimientos(List<Movimiento> nuevos) { movimientos.clear(); for (Movimiento m : nuevos) if (m.getLatitud()!=null && m.getLongitud()!=null) movimientos.add(m); invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.drawColor(Color.rgb(245,243,239)); paint.setColor(Color.rgb(210,225,218));
        for(int x=0;x<getWidth();x+=48) canvas.drawLine(x,0,x,getHeight(),paint);
        for(int y=0;y<getHeight();y+=48) canvas.drawLine(0,y,getWidth(),y,paint);
        if(movimientos.isEmpty()) return;
        double minLat=movimientos.get(0).getLatitud(), maxLat=minLat, minLon=movimientos.get(0).getLongitud(), maxLon=minLon;
        for(Movimiento m:movimientos) { minLat=Math.min(minLat,m.getLatitud()); maxLat=Math.max(maxLat,m.getLatitud()); minLon=Math.min(minLon,m.getLongitud()); maxLon=Math.max(maxLon,m.getLongitud()); }
        double latSpan=Math.max(.0005,maxLat-minLat), lonSpan=Math.max(.0005,maxLon-minLon); paint.setColor(Color.rgb(200,91,23));
        for(Movimiento m:movimientos) { float x=(float)(32+(m.getLongitud()-minLon)/lonSpan*(getWidth()-64)+offsetX); float y=(float)(getHeight()-32-(m.getLatitud()-minLat)/latSpan*(getHeight()-64)+offsetY); canvas.drawCircle(x,y,11,paint); }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();return true;}
        if(event.getAction()==MotionEvent.ACTION_MOVE){offsetX+=event.getX()-downX;offsetY+=event.getY()-downY;downX=event.getX();downY=event.getY();invalidate();return true;}
        return true;
    }
}
