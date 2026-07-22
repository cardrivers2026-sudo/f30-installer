package com.cool.appinstaller;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String BASE_URL = "http://51.38.112.107/api/";
    private EditText etPhone;
    private TextView tvStatus;
    private RecyclerView rv;
    private AppAdapter adapter;
    private List<AppModel> appList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etPhone = findViewById(R.id.etPhone);
        tvStatus = findViewById(R.id.tvStatus);
        Button btnLoad = findViewById(R.id.btnLoad);
        rv = findViewById(R.id.rvApps);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppAdapter(appList, app -> {
            runOnUiThread(() -> tvStatus.setText("Starting: " + app.name));
            AppInstaller.downloadAndInstall(
                getApplicationContext(),
                app.app_link,
                app.name,
                status -> runOnUiThread(() -> tvStatus.setText(status))
            );
        });
        rv.setAdapter(adapter);

        etPhone.setText("015400006674701");

        btnLoad.setOnClickListener(v -> {
            String id = etPhone.getText().toString().trim();
            if(id.isEmpty()){
                Toast.makeText(this,"Enter ID",Toast.LENGTH_SHORT).show();
                return;
            }
            loadApps(id);
        });
    }

    private void loadApps(String id){
        tvStatus.setText("Logging in...");
        new Thread(() -> {
            try {
                // 1. f30_login
                postForm(BASE_URL+"auth/f30_login", "phone="+id+"&password="+id+"&is_phone=1&imei="+id+"&model=F30&mac=a9032d5763fb5ecc");
                runOnUiThread(() -> tvStatus.setText("Login OK, getting user..."));

                // 2. getF30User
                String userResp = postForm(BASE_URL+"auth/getF30User", "password="+id+"&model=F30");
                String level = "3";
                try{
                    JSONObject uj = new JSONObject(userResp);
                    if(uj.has("level")) level = uj.getString("level");
                    else if(uj.has("data")) level = uj.getJSONObject("data").optString("level","3");
                }catch(Exception e){}

                final String finalLevel = level;
                runOnUiThread(() -> tvStatus.setText("Level "+finalLevel+" - Loading apps..."));

                // 3. f30_getAllApps
                String appsResp = postForm(BASE_URL+"f30_getAllApps", "password="+id+"&level="+finalLevel+"&model=F30&email="+id+"@f30.com");

                JSONObject json = new JSONObject(appsResp);
                JSONArray arr;
                if(json.has("data")) arr = json.getJSONArray("data");
                else arr = new JSONArray(appsResp);

                appList.clear();
                for(int i=0;i<arr.length();i++){
                    JSONObject o = arr.getJSONObject(i);
                    String name = o.optString("name", o.optString("app_name","App "+i));
                    String link = o.optString("app_link","");
                    if(!link.isEmpty()) appList.add(new AppModel(name, link));
                }

                runOnUiThread(() -> {
                    tvStatus.setText("Found "+appList.size()+" apps");
                    adapter.notifyDataSetChanged();
                });

            }catch(Exception e){
                runOnUiThread(() -> tvStatus.setText("Error: "+e.getMessage()));
            }
        }).start();
    }

    private String postForm(String urlStr, String formData) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
        try(OutputStream os = conn.getOutputStream()){ os.write(formData.getBytes()); }
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder(); String line;
        while((line=br.readLine())!=null) sb.append(line);
        return sb.toString();
    }

    static class AppModel { String name, app_link; AppModel(String n,String l){name=n;app_link=l;} }

    static class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH>{
        List<AppModel> list; OnInstallClick listener;
        interface OnInstallClick{ void onInstall(AppModel app); }
        AppAdapter(List<AppModel> l, OnInstallClick li){list=l;listener=li;}
        @Override public VH onCreateViewHolder(android.view.ViewGroup p,int t){
            LinearLayout layout = new LinearLayout(p.getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(30,30,30,30);
            TextView tv = new TextView(p.getContext());
            Button btn = new Button(p.getContext()); btn.setText("INSTALL THIS APP");
            layout.addView(tv); layout.addView(btn);
            return new VH(layout, tv, btn);
        }
        @Override public void onBindViewHolder(VH h,int pos){
            AppModel app = list.get(pos);
            h.tv.setText(app.name);
            h.btn.setOnClickListener(v->listener.onInstall(app));
        }
        @Override public int getItemCount(){return list.size();}
        static class VH extends RecyclerView.ViewHolder{
            TextView tv; Button btn;
            VH(android.view.View v, TextView tv, Button btn){super(v);this.tv=tv;this.btn=btn;}
        }
    }
}
