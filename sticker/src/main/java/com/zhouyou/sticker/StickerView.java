package com.zhouyou.sticker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ImageView;

import androidx.core.view.MotionEventCompat;

import com.zhouyou.sticker.utils.PointUtils;

/**
 * 作者：ZhouYou
 * 日期：2016/12/2.
 */
public class StickerView extends androidx.appcompat.widget.AppCompatImageView {

    private Context context;
    // 被操作的贴纸对象
    private Sticker sticker;
    // 手指按下时图片的矩阵
    private Matrix downMatrix = new Matrix();
    // 手指移动时图片的矩阵
    private Matrix moveMatrix = new Matrix();
    // 多点触屏时的中心点
    private PointF midPoint = new PointF();
    // 图片的中心点坐标
    private PointF imageMidPoint = new PointF();
    // 旋转操作图片
    private StickerActionIcon rotateIcon;
    // 缩放操作图片
    private StickerActionIcon zoomIcon;
    // 缩放操作图片
    private StickerActionIcon removeIcon;
    // 绘制图片的边框
    private Paint paintEdge;

    // 触控模式
    private int mode;
    // 是否正在处于编辑
    private boolean isEdit = true;
    // 贴纸的操作监听
    private OnStickerActionListener listener;
    // 手指按下屏幕的X坐标
    private float downX;
    // 手指按下屏幕的Y坐标
    private float downY;
    // 手指之间的初始距离
    private float oldDistance;
    // 手指之间的初始角度
    private float oldRotation;

    public StickerView(Context context) {
        this(context, null);
    }

    public StickerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StickerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void setOnStickerActionListener(OnStickerActionListener listener) {
        this.listener = listener;
    }

    private void init(Context context) {
        this.context = context;
        setScaleType(ScaleType.MATRIX);
        rotateIcon = new StickerActionIcon(context);
        zoomIcon = new StickerActionIcon(context);
        removeIcon = new StickerActionIcon(context);
        paintEdge = new Paint();
        paintEdge.setColor(Color.WHITE);
        paintEdge.setAlpha(170);
        paintEdge.setStrokeWidth(5f);
        paintEdge.setAntiAlias(true);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            sticker.getMatrix().postTranslate((getWidth() - sticker.getStickerWidth()) / 2, (getHeight() - sticker.getStickerHeight()) / 2);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (sticker == null) return;
        sticker.draw(canvas);
        float[] points = PointUtils.getBitmapPoints(sticker.getSrcImage(), sticker.getMatrix());
        float x1 = points[0];
        float y1 = points[1];
        float x2 = points[2];
        float y2 = points[3];
        float x3 = points[4];
        float y3 = points[5];
        float x4 = points[6];
        float y4 = points[7];
        if (isEdit) {
            // 画边框
            canvas.drawLine(x1, y1, x2, y2, paintEdge);
            canvas.drawLine(x2, y2, x4, y4, paintEdge);
            canvas.drawLine(x4, y4, x3, y3, paintEdge);
            canvas.drawLine(x3, y3, x1, y1, paintEdge);
            // 画操作按钮图片
            rotateIcon.draw(canvas, x2, y2);
            zoomIcon.draw(canvas, x3, y3);
            removeIcon.draw(canvas, x1, y1);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = MotionEventCompat.getActionMasked(event);
        boolean isStickerOnEdit = true;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                if (sticker == null) return false;
                // 删除操作
                if (removeIcon.isInActionCheck(event)) {
                    if (listener != null) {
                        listener.onDelete();
                    }
                }
                // 旋转手势验证
                else if (rotateIcon.isInActionCheck(event)) {
                    mode = ActionMode.ROTATE;
                    downMatrix.set(sticker.getMatrix());
                    imageMidPoint = sticker.getImageMidPoint(downMatrix);
                    oldRotation = sticker.getSpaceRotation(event, imageMidPoint);
                    Log.d("onTouchEvent", "旋转手势");
                }
                // 单点缩放手势验证
                else if (zoomIcon.isInActionCheck(event)) {
                    mode = ActionMode.ZOOM_SINGLE;
                    downMatrix.set(sticker.getMatrix());
                    imageMidPoint = sticker.getImageMidPoint(downMatrix);
                    oldDistance = sticker.getSingleTouchDistance(event, imageMidPoint);
                    Log.d("onTouchEvent", "单点缩放手势");
                }
                // 平移手势验证
                else if (isInStickerArea(sticker, event)) {
                    mode = ActionMode.TRANS;
                    downMatrix.set(sticker.getMatrix());
                    Log.d("onTouchEvent", "平移手势");
                } else {
                    isStickerOnEdit = false;
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN: // 多点触控
                mode = ActionMode.ZOOM_MULTI;
                oldDistance = sticker.getMultiTouchDistance(event);
                midPoint = sticker.getMidPoint(event);
                downMatrix.set(sticker.getMatrix());
                break;
            case MotionEvent.ACTION_MOVE:
                // 单点旋转
                if (mode == ActionMode.ROTATE) {
                    moveMatrix.set(downMatrix);
                    float deltaRotation = sticker.getSpaceRotation(event, imageMidPoint) - oldRotation;
                    moveMatrix.postRotate(deltaRotation, imageMidPoint.x, imageMidPoint.y);
                    sticker.getMatrix().set(moveMatrix);
                    invalidate();
                }
                // 单点缩放
                else if (mode == ActionMode.ZOOM_SINGLE) {
                    moveMatrix.set(downMatrix);
                    float scale = sticker.getSingleTouchDistance(event, imageMidPoint) / oldDistance;
                    moveMatrix.postScale(scale, scale, imageMidPoint.x, imageMidPoint.y);
                    sticker.getMatrix().set(moveMatrix);
                    invalidate();
                }
                // 多点缩放
                else if (mode == ActionMode.ZOOM_MULTI) {
                    moveMatrix.set(downMatrix);
                    float scale = sticker.getMultiTouchDistance(event) / oldDistance;
                    moveMatrix.postScale(scale, scale, midPoint.x, midPoint.y);
                    sticker.getMatrix().set(moveMatrix);
                    invalidate();
                }
                // 平移
                else if (mode == ActionMode.TRANS) {
                    moveMatrix.set(downMatrix);
                    moveMatrix.postTranslate(event.getX() - downX, event.getY() - downY);
                    sticker.getMatrix().set(moveMatrix);
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                mode = ActionMode.NONE;
                midPoint = null;
                imageMidPoint = null;
                break;
            default:
                break;
        }
        if (isStickerOnEdit && listener != null) {
            listener.onEdit(this);
        }
        return isStickerOnEdit;
    }

    /**
     * 判断手指是否在操作区域内
     *
     * @param sticker
     * @param event
     * @return
     */
    private boolean isInStickerArea(Sticker sticker, MotionEvent event) {
        RectF dst = sticker.getSrcImageBound();
        return dst.contains(event.getX(), event.getY());
    }

    /**
     * 添加贴纸
     *
     * @param resId
     */
    @Override
    public void setImageResource(int resId) {
        sticker = new Sticker(BitmapFactory.decodeResource(context.getResources(), resId));
    }

    /**
     * 获取贴纸对象
     *
     * @return
     */
    public Sticker getSticker() {
        return sticker;
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        sticker = new Sticker(bm);
    }

    /**
     * 设置是否贴纸正在处于编辑状态
     *
     * @param edit
     */
    public void setEdit(boolean edit) {
        isEdit = edit;
        postInvalidate();
    }

    /**
     * 设置旋转操作的图片
     *
     * @param rotateRes
     */
    public void setRotateRes(int rotateRes) {
        rotateIcon.setSrcIcon(rotateRes);
    }

    /**
     * 设置缩放操作的图片
     *
     * @param zoomRes
     */
    public void setZoomRes(int zoomRes) {
        zoomIcon.setSrcIcon(zoomRes);
    }

    /**
     * 设置删除操作的图片
     *
     * @param removeRes
     */
    public void setRemoveRes(int removeRes) {
        removeIcon.setSrcIcon(removeRes);
    }
}
