package com.example.learninghelper;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/** Snapshots stay in the private database. Pending preference delivery survives process death. */
final class DataProfiles {
    static void initialize(Context c,SQLiteDatabase db)throws Exception{
        db.execSQL("CREATE TABLE IF NOT EXISTS data_profiles(name TEXT PRIMARY KEY, payload TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS data_profile_state(id INTEGER PRIMARY KEY, active TEXT NOT NULL, pending TEXT)");
        try(Cursor state=db.rawQuery("SELECT active FROM data_profile_state WHERE id=1",null)){
            if(!state.moveToFirst()){
                boolean demo=false;
                JSONArray todos=new JSONArray(c.getSharedPreferences("learning_todos",0).getString("items","[]"));
                for(int i=0;i<todos.length();i++)if(todos.getJSONObject(i).optString("id").startsWith("demo-20261118-"))demo=true;
                ContentValues v=new ContentValues();v.put("id",1);v.put("active",demo?"demo":"personal");db.insertOrThrow("data_profile_state",null,v);
            }
        }
        recover(c,db);
    }
    static String account(Context c){return c.getSharedPreferences("local_accounts",0).getString("active","personal");}
    static SharedPreferences profile(Context c,String id){return c.getSharedPreferences(id.equals("demo")?"demo_profile":"personal_profile",0);}
    static java.io.File avatar(Context c,String id){return new java.io.File(c.getFilesDir(),id.equals("demo")?"demo-avatar.png":"profile-avatar.png");}
    static String active(SQLiteDatabase db){try(Cursor c=db.rawQuery("SELECT active FROM data_profile_state WHERE id=1",null)){c.moveToFirst();return c.getString(0);}}
    static void recover(Context c,SQLiteDatabase db)throws Exception{
        String pending=null;try(Cursor r=db.rawQuery("SELECT pending FROM data_profile_state WHERE id=1",null)){if(r.moveToFirst())pending=r.getString(0);}
        if(pending!=null){JSONObject data=new JSONObject(pending);Set<String> categories=new HashSet<>();JSONArray a=data.getJSONArray("categories");for(int i=0;i<a.length();i++)categories.add(a.getString(i));
            if(!c.getSharedPreferences("learning_todos",0).edit().putString("items",data.getJSONArray("todos").toString()).putStringSet("categories",categories).commit())throw new java.io.IOException("Unable to save tasks");

        }
        if(!c.getSharedPreferences("local_accounts",0).edit().putString("active",active(db)).commit())throw new java.io.IOException("Unable to save active account");
        if(pending!=null)db.execSQL("UPDATE data_profile_state SET pending=NULL WHERE id=1");
    }
    static void switchTo(Context c,SQLiteDatabase db,String target)throws Exception{
        initialize(c,db);if(!target.equals("personal")&&!target.equals("demo"))throw new IllegalArgumentException("Unknown data profile");
        if(target.equals(active(db)))return;
        try(Cursor r=db.rawQuery("SELECT 1 FROM sessions WHERE status=0 LIMIT 1",null)){if(r.moveToFirst())throw new IllegalStateException(AppUi.label(c,"请先结束当前专注再切换数据","Finish the current focus session before switching data"));}
        JSONObject next=null;try(Cursor r=db.rawQuery("SELECT payload FROM data_profiles WHERE name=?",new String[]{target})){if(r.moveToFirst())next=new JSONObject(r.getString(0));}
        if(next==null)next=target.equals("demo")?DemoData.create(c):new JSONObject("{\"todos\":[],\"categories\":[],\"tasks\":[],\"sessions\":[]}");
        JSONObject current=snapshot(c,db);String before=active(db);
        db.beginTransaction();try{
            ContentValues saved=new ContentValues();saved.put("name",before);saved.put("payload",current.toString());db.insertWithOnConflict("data_profiles",null,saved,SQLiteDatabase.CONFLICT_REPLACE);
            for(String table:new String[]{"sessions","tasks"}){
                db.delete(table,null,null);JSONArray rows=next.getJSONArray(table);
                for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);ContentValues v=new ContentValues();Iterator<String> keys=row.keys();while(keys.hasNext()){String k=keys.next();Object value=row.get(k);if(value==JSONObject.NULL)v.putNull(k);else if(value instanceof Number)v.put(k,((Number)value).longValue());else v.put(k,value.toString());}db.insertOrThrow(table,null,v);}
            }
            ContentValues state=new ContentValues();state.put("active",target);state.put("pending",next.toString());db.update("data_profile_state",state,"id=1",null);db.setTransactionSuccessful();
        }finally{db.endTransaction();}recover(c,db);
    }
    private static JSONObject snapshot(Context c,SQLiteDatabase db)throws Exception{
        SharedPreferences p=c.getSharedPreferences("learning_todos",0);JSONObject result=new JSONObject().put("todos",new JSONArray(p.getString("items","[]"))).put("categories",new JSONArray(p.getStringSet("categories",Collections.emptySet())));
        for(String table:new String[]{"tasks","sessions"}){JSONArray rows=new JSONArray();try(Cursor r=db.query(table,null,null,null,null,null,null)){while(r.moveToNext()){JSONObject row=new JSONObject();for(int i=0;i<r.getColumnCount();i++)row.put(r.getColumnName(i),r.isNull(i)?JSONObject.NULL:r.getType(i)==Cursor.FIELD_TYPE_INTEGER?r.getLong(i):r.getString(i));rows.put(row);}}result.put(table,rows);}return result;
    }
}
