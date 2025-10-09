package nl.mikekemmink.noodverlichting.views;

import android.content.Context;
import android.graphics.Matrix;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import androidx.appcompat.widget.AppCompatImageView;

public class ZoomPanImageView extends AppCompatImageView {
    private final Matrix matrix = new Matrix();
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private OnMatrixChanged onMatrixChanged;

    public ZoomPanImageView(Context c, android.util.AttributeSet a) {
        super(c, a);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scale = detector.getScaleFactor();
                matrix.postScale(scale, scale, detector.getFocusX(), detector.getFocusY());
                setImageMatrix(matrix);
                if (onMatrixChanged != null) onMatrixChanged.onChanged(getImageMatrix());
                return true;
            }
        });
        gestureDetector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                matrix.postTranslate(-dx, -dy);
                setImageMatrix(matrix);
                if (onMatrixChanged != null) onMatrixChanged.onChanged(getImageMatrix());
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                matrix.reset();
                setImageMatrix(matrix);
                if (onMatrixChanged != null) onMatrixChanged.onChanged(getImageMatrix());
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean ret = scaleDetector.onTouchEvent(event);
        ret = gestureDetector.onTouchEvent(event) || ret;
        return ret || super.onTouchEvent(event);
    }

    public interface OnMatrixChanged {
        void onChanged(Matrix m);
    }

    public void setOnMatrixChanged(OnMatrixChanged l) {
        this.onMatrixChanged = l;
    }

    public Matrix getCurrentMatrix() {
        return new Matrix(getImageMatrix());
    }
}
