package com.nickhe.hackernews;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    DownloadTask task;
    ListView listView;
    ArrayList<String> titles = new ArrayList<>();
    ArrayList<String> contents = new ArrayList<>();
    ArrayAdapter adapter;
    SQLiteDatabase articleDB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.listView);

        adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, titles);

        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                Intent intent = new Intent(getApplicationContext(), ArticleActivity.class);
                intent.putExtra("content", contents.get(position));

                startActivity(intent);
            }
        });

        articleDB = this.openOrCreateDatabase("articles", MODE_PRIVATE, null);

        articleDB.execSQL("CREATE TABLE IF NOT EXISTS articles (id INTEGER PRIMARY KEY, articleId INTEGER, title VARCHAR, content VARCHAR)");

        //Call the update method before starting download task
        updateListView();

        task = new DownloadTask();

        try {
            //task.execute("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void updateListView()
    {
        Cursor c = articleDB.rawQuery("SELECT * FROM articles", null);

        int titleIndex = c.getColumnIndex("title");
        int contentIndex = c.getColumnIndex("content");

        if(c.moveToFirst())
        {
            titles.clear();
            contents.clear();

            int counter = 1;

            do{
                titles.add(counter++ + ". "+c.getString(titleIndex));
                contents.add(c.getString(contentIndex));
            }while(c.moveToNext());

            adapter.notifyDataSetChanged();
        }
    }

    public class DownloadTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... strings) {

            String result = "";
            URL url;
            HttpURLConnection urlConnection = null;

            try {
                url = new URL(strings[0]);

                urlConnection = (HttpURLConnection) url.openConnection();

                InputStream in = urlConnection.getInputStream();

                InputStreamReader reader = new InputStreamReader(in);

                int data = reader.read();

                while (data != -1) {
                    char current = (char) data;

                    result += current;

                    data = reader.read();
                }

                JSONArray array = new JSONArray(result);

                int numOfItems = 20;

                if (array.length() < 20) {
                    numOfItems = array.length();
                }

                //Make sure we don't add same thing again
                articleDB.execSQL("DELETE FROM articles");

                //Get all the article json file from each id
                for (int i = 0; i < numOfItems; i++) {

                    String articleId = array.getString(i);

                    url = new URL("https://hacker-news.firebaseio.com/v0/item/" +
                            articleId + ".json?print=pretty");

                    urlConnection = (HttpURLConnection) url.openConnection();

                    in = urlConnection.getInputStream();

                    reader = new InputStreamReader(in);

                    data = reader.read();

                    String content = "";

                    while (data != -1) {
                        char current = (char) data;

                        content += current;

                        data = reader.read();
                    }


                    JSONObject object = new JSONObject(content);

                    if (!object.isNull("title") && !object.isNull("url")) {

                        String articleTitle = object.getString("title");
                        String articleUrl = object.getString("url");

                        url = new URL(articleUrl);

                        urlConnection = (HttpURLConnection) url.openConnection();

                        in = urlConnection.getInputStream();

                        reader = new InputStreamReader(in);

                        data = reader.read();

                        String articleContent = "";

                        while (data != -1) {
                            char current = (char) data;

                            articleContent += current;

                            data = reader.read();
                        }

                        String sql = "INSERT INTO articles (articleID, title, content) VALUES(?, ?, ?)";

                        SQLiteStatement statement = articleDB.compileStatement(sql);

                        statement.bindString(1, articleId);
                        statement.bindString(2, articleTitle);
                        statement.bindString(3, articleContent);

                        statement.execute();

                        System.out.println("No."+i+" finished");
                    }

                }

            } catch (Exception e) {

                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            System.out.println("Downloading done.");
            updateListView();
        }
    }

}
