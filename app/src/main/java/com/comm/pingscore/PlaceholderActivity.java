package com.comm.pingscore;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class PlaceholderActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_placeholder);
        findViewById(R.id.placeholder_back).setOnClickListener(v -> finish());
    }
}
