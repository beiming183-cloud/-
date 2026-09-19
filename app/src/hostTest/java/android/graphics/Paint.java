package android.graphics;
public class Paint {
 public static final int ANTI_ALIAS_FLAG=1,SUBPIXEL_TEXT_FLAG=128;
 private float size;
 public Paint(int flags){}
 public static class FontMetrics {public float ascent,descent;}
 public Typeface setTypeface(Typeface typeface){return typeface;}
 public void setTextSize(float size){this.size=size;}
 public float measureText(String text){return text.length()*size*0.5f;}
 public float getFontMetrics(FontMetrics metrics){metrics.ascent=-size*0.8f;metrics.descent=size*0.2f;return size;}
}
