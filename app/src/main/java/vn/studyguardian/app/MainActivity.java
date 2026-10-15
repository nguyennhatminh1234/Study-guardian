package vn.studyguardian.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class MainActivity extends Activity {
    private final ArrayList<TaskItem> tasks = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout root, taskList;
    private EditText taskInput, homeworkInput, apiInput;
    private TextView status, verifyResult, timerText;
    private Uri selectedImage;
    private String sharedText = "";
    private android.content.SharedPreferences prefs;
    private boolean sessionRunning = false;
    private long secondsLeft = 0;
    private android.os.Handler handler = new android.os.Handler();
    private Runnable ticker;

    static class TaskItem {
        String title; boolean done;
        TaskItem(String t) { title=t; done=false; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("study_guardian", MODE_PRIVATE);
        buildUi();
        handleIncoming(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncoming(intent);
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView text(String s, float size, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(0xFF182230); t.setPadding(0, dp(4), 0, dp(4)); return t;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private LinearLayout section(String title) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16),dp(12),dp(16),dp(12));
        box.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(6),0,dp(6));
        root.addView(box,p);
        TextView h = text(title,18,true); box.addView(h);
        return box;
    }
    private EditText edit(String hint, int lines) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextSize(16);
        e.setPadding(dp(10),dp(8),dp(10),dp(8)); e.setMinHeight(dp(48));
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF8A96A8));
        if(lines>1){ e.setMinLines(lines); e.setGravity(Gravity.TOP); }
        return e;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(12),dp(14),dp(18)); root.setBackgroundColor(0xFFF3F5F9);
        scroll.addView(root); setContentView(scroll);

        LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18),dp(18),dp(18),dp(18)); hero.setBackgroundColor(0xFFE5ECFF);
        root.addView(hero, new LinearLayout.LayoutParams(-1,-2));
        hero.addView(text("STUDY GUARDIAN",13,true));
        hero.addView(text("Học xong rồi chơi.",27,true));
        hero.addView(text("Quản lý bài tập · Kiểm tra ảnh bằng AI",14,false));

        LinearLayout tasksBox = section("01 · Danh sách bài tập");
        taskInput = edit("Ví dụ: Toán trang 35, bài 1–5",1); tasksBox.addView(taskInput);
        tasksBox.addView(button("＋ Thêm bài tập", () -> addTask()));
        taskList = new LinearLayout(this); taskList.setOrientation(LinearLayout.VERTICAL); tasksBox.addView(taskList);
        status = text("Chưa có bài tập.",14,false); tasksBox.addView(status);

        LinearLayout shareBox = section("02 · Nhận bài từ Zalo");
        shareBox.addView(text("Trong Zalo, chọn Chia sẻ → Study Guardian. Bạn cũng có thể dán nội dung đề bên dưới.",14,false));
        homeworkInput = edit("Dán đề bài hoặc nội dung tin nhắn...",4); shareBox.addView(homeworkInput);
        shareBox.addView(button("Chọn ảnh bài làm", () -> chooseImage()));
        shareBox.addView(button("Kiểm tra bài làm bằng AI", () -> verifyWithAi()));

        LinearLayout apiBox = section("03 · Cài đặt AI");
        apiBox.addView(text("Nhập Gemini API key để bật kiểm tra ảnh và đáp án. Khóa được lưu trên thiết bị này. Không chia sẻ APK có chứa khóa của bạn.",13,false));
        apiInput = edit("Gemini API key",1); apiInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiInput.setText(prefs.getString("api_key","")); apiBox.addView(apiInput);
        apiBox.addView(button("Lưu khóa AI", () -> {
            prefs.edit().putString("api_key",apiInput.getText().toString().trim()).apply();
            toast("Đã lưu khóa trên thiết bị.");
        }));
        verifyResult = text("Chưa kiểm tra. Ảnh trang trắng hoặc ảnh không liên quan không được xem là bằng chứng hoàn thành.",14,false);
        apiBox.addView(verifyResult);

        LinearLayout gate = section("04 · Điều kiện giải trí");
        gate.addView(text("Đánh dấu bài tập hoàn thành chỉ sau khi đã làm thật và kiểm tra kết quả. AI hỗ trợ đánh giá, không thể chứng minh tuyệt đối ai đã làm bài.",14,false));
        timerText = text("Chưa mở phiên giải trí",22,true); gate.addView(timerText);
        gate.addView(button("Bắt đầu 30 phút giải trí", () -> startSession()));
        gate.addView(button("Kết thúc phiên", () -> stopSession("Đã kết thúc phiên giải trí.")));
        gate.addView(text("Lưu ý: phiên này chỉ là bộ đếm và nhắc nhở. Ứng dụng chưa khóa được Zalo hay các ứng dụng khác.",12,false));
        renderTasks();
    }

    private void addTask() {
        String s = taskInput.getText().toString().trim();
        if(s.isEmpty()) { toast("Nhập tên bài tập trước."); return; }
        tasks.add(new TaskItem(s)); taskInput.setText(""); renderTasks();
    }
    private void renderTasks() {
        if(taskList==null) return;
        taskList.removeAllViews();
        int done=0;
        for(int i=0;i<tasks.size();i++){
            final int index=i; TaskItem item=tasks.get(i); if(item.done) done++;
            LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox cb=new CheckBox(this); cb.setChecked(item.done);
            cb.setOnCheckedChangeListener((v,checked)->{item.done=checked;renderTasks();});
            row.addView(cb);
            TextView name=text(item.title,15,false); name.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1)); row.addView(name);
            Button del=new Button(this); del.setText("Xóa"); del.setOnClickListener(v->{tasks.remove(index);renderTasks();});
            row.addView(del); taskList.addView(row);
        }
        status.setText("Hoàn thành "+done+"/"+tasks.size()+" bài. Hãy kiểm tra tin nhắn Zalo mới trước khi kết thúc buổi học.");
    }

    private void chooseImage() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,101);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==101 && resultCode==RESULT_OK && data!=null) {
            selectedImage=data.getData();
            try { getContentResolver().takePersistableUriPermission(selectedImage,Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored){}
            verifyResult.setText("Đã chọn ảnh. Nhấn “Kiểm tra bài làm bằng AI” để phân tích.");
        }
    }
    private void handleIncoming(Intent intent) {
        if(intent==null) return;
        String action=intent.getAction(), type=intent.getType();
        if(Intent.ACTION_SEND.equals(action)) {
            if(type!=null && type.startsWith("image/")) {
                selectedImage=intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if(selectedImage!=null) verifyResult.setText("Đã nhận ảnh được chia sẻ. Nhấn kiểm tra AI.");
            } else {
                String s=intent.getStringExtra(Intent.EXTRA_TEXT);
                if(s!=null) { sharedText=s; if(homeworkInput!=null) homeworkInput.setText(s); }
            }
        } else if(Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> uris=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if(uris!=null && !uris.isEmpty()) { selectedImage=uris.get(0); verifyResult.setText("Đã nhận ảnh được chia sẻ. Bản đầu xử lý từng ảnh một."); }
        }
    }

    private void verifyWithAi() {
        final String key=apiInput.getText().toString().trim();
        final String prompt=homeworkInput.getText().toString().trim();
        if(key.isEmpty()){ toast("Hãy nhập và lưu Gemini API key trước."); return; }
        if(selectedImage==null && prompt.isEmpty()){ toast("Hãy dán đề hoặc chọn ảnh bài làm."); return; }
        verifyResult.setText("Đang kiểm tra... cần mạng Internet.");
        worker.execute(() -> {
            try {
                JSONObject request=new JSONObject();
                JSONArray contents=new JSONArray(); JSONObject content=new JSONObject(); JSONArray parts=new JSONArray();
                String instructions="Bạn là trợ lý kiểm tra bài tập cho học sinh. Đánh giá thận trọng, bằng tiếng Việt. Đề/tin nhắn được cung cấp: "+prompt+
                    "\nNếu có ảnh, hãy kiểm tra ảnh có trống, có chữ viết/ phép tính thật, có liên quan đến đề không; đọc được bao nhiêu câu; đối chiếu đáp án và giải thích lỗi. Không khẳng định chắc chắn khi ảnh mờ hoặc không đủ thông tin. Không đánh dấu hoàn thành chỉ vì có chữ. Trả lời theo mục: KẾT LUẬN (CHƯA ĐỦ BẰNG CHỨNG / CẦN SỬA / CÓ THỂ ĐẠT), NỘI DUNG NHÌN THẤY, CÂU ĐÃ LÀM, LỖI CẦN SỬA, ĐỘ TIN CẬY. Nếu chỉ có đề văn bản mà không có ảnh bài làm, nói rõ chưa thể xác minh bài đã làm.";
                JSONObject p=new JSONObject(); p.put("text",instructions); parts.put(p);
                if(selectedImage!=null) {
                    byte[] bytes=readBytes(selectedImage);
                    String mime=getContentResolver().getType(selectedImage); if(mime==null || !mime.startsWith("image/")) mime="image/jpeg";
                    JSONObject inline=new JSONObject(); inline.put("mimeType",mime); inline.put("data",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP));
                    JSONObject ip=new JSONObject(); ip.put("inlineData",inline); parts.put(ip);
                }
                content.put("parts",parts); contents.put(content); request.put("contents",contents);
                JSONObject gen=new JSONObject(); gen.put("temperature",0.2); gen.put("maxOutputTokens",1200); request.put("generationConfig",gen);
                URL url=new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="+key);
                HttpURLConnection conn=(HttpURLConnection)url.openConnection(); conn.setRequestMethod("POST");
                conn.setConnectTimeout(20000); conn.setReadTimeout(45000); conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                try(OutputStream os=conn.getOutputStream()){os.write(request.toString().getBytes(StandardCharsets.UTF_8));}
                InputStream stream=(conn.getResponseCode()<400)?conn.getInputStream():conn.getErrorStream();
                ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[8192]; int n;
                while((n=stream.read(buf))!=-1) out.write(buf,0,n);
                String response=new String(out.toByteArray(),StandardCharsets.UTF_8);
                if(conn.getResponseCode()>=400) throw new Exception("Lỗi API ("+conn.getResponseCode()+"): "+response);
                JSONObject obj=new JSONObject(response);
                String result=obj.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
                runOnUiThread(()->verifyResult.setText(result+"\n\nLưu ý: AI có thể nhầm. Hãy tự xem lại trước khi đánh dấu hoàn thành."));
                conn.disconnect();
            } catch(Exception ex) {
                String msg=ex.getMessage()==null?"Không xác định":ex.getMessage();
                runOnUiThread(()->verifyResult.setText("Không kiểm tra được: "+msg));
            }
        });
    }
    private byte[] readBytes(Uri uri) throws Exception {
        try(InputStream in=getContentResolver().openInputStream(uri); ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null) throw new Exception("Không mở được ảnh");
            byte[] b=new byte[8192]; int n; int total=0;
            while((n=in.read(b))!=-1){total+=n;if(total>12*1024*1024)throw new Exception("Ảnh quá lớn (giới hạn 12 MB).");out.write(b,0,n);}
            return out.toByteArray();
        }
    }
    private void startSession() {
        if(sessionRunning) return;
        sessionRunning=true; secondsLeft=1800; timerText.setText("30:00 · Đang chạy");
        ticker=new Runnable(){public void run(){if(!sessionRunning)return;secondsLeft--;if(secondsLeft<=0){stopSession("Hết giờ giải trí. Hãy quay lại việc học hoặc nghỉ ngơi.");return;}
            timerText.setText(String.format(java.util.Locale.getDefault(),"%02d:%02d · Đang chạy",secondsLeft/60,secondsLeft%60));handler.postDelayed(this,1000);}};
        handler.postDelayed(ticker,1000);
        new AlertDialog.Builder(this).setTitle("Phiên giải trí bắt đầu").setMessage("Đây là bộ đếm nhắc nhở, không khóa ứng dụng khác.").setPositiveButton("Hiểu rồi",null).show();
    }
    private void stopSession(String msg) {
        sessionRunning=false; if(ticker!=null)handler.removeCallbacks(ticker); timerText.setText(msg);
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){super.onDestroy();if(ticker!=null)handler.removeCallbacks(ticker);worker.shutdownNow();}
}
