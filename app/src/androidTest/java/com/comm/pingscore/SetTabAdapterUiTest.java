package com.comm.pingscore;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public final class SetTabAdapterUiTest {
    @Test
    public void bindRendersCurrentAndPendingSetLabels() {
        Context context = ApplicationProvider.getApplicationContext();
        SetTabAdapter adapter = new SetTabAdapter(Arrays.asList(
                new SetTabAdapter.Item(2, SetTabAdapter.CURRENT, "0:0", true),
                new SetTabAdapter.Item(3, SetTabAdapter.PENDING, "待赛", false)),
                position -> { });
        FrameLayout parent = new FrameLayout(context);

        SetTabAdapter.TabViewHolder currentHolder = adapter.onCreateViewHolder(parent, 0);
        SetTabAdapter.TabViewHolder pendingHolder = adapter.onCreateViewHolder(parent, 0);

        adapter.onBindViewHolder(currentHolder, 0);
        adapter.onBindViewHolder(pendingHolder, 1);

        assertEquals("第2局\n0:0", currentHolder.label.getText().toString());
        assertEquals("第3局\n待赛", pendingHolder.label.getText().toString());
    }
}
