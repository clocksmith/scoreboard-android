package com.gamesmith.scoreboard.room;

import android.graphics.Rect;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;

import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import com.gamesmith.scoreboard.R;
import com.gamesmith.scoreboard.common.BusProvider;
import com.gamesmith.scoreboard.firebase.FirebaseUtils;
import com.gamesmith.scoreboard.firebase.Player;
import com.gamesmith.scoreboard.common.Constants;
import com.google.common.collect.Lists;
import com.squareup.otto.Subscribe;

import java.util.List;

/**
 * Created by clocksmith on 5/24/15.
 */
public class RoomActivity extends AppCompatActivity {
  private static final String TAG = RoomActivity.class.getSimpleName();

  private Firebase mFirebase;
  private RoomValueEventListener mRoomValueEventListener;
  private int mRoomNumber;
  private int mPlayerId;

  private MainPlayerCard mMainPlayerCard;
  private RecyclerView mRecyclerView;
  private UserRecyclerViewAdapter mAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_room);

    BusProvider.getInstance().register(this);
    mFirebase = new Firebase(Constants.FIREBASE_URL);

    Bundle bundle = getIntent().getExtras();
    if (bundle != null) {
      mRoomNumber = bundle.getInt(Constants.ROOM_NUMBER);
      mPlayerId = bundle.getInt(Constants.PLAYER_ID);
    }

    mMainPlayerCard = (MainPlayerCard) findViewById(R.id.activity_room_mainUserCard);
    mRecyclerView = (RecyclerView) findViewById(R.id.activity_room_recyclerView);

    mAdapter = new UserRecyclerViewAdapter();
    mRecyclerView.setAdapter(mAdapter);
    mRecyclerView.setLayoutManager(new GridLayoutManager(this, Constants.NUM_COLUMNS_IN_USER_GRID));
    mRecyclerView.addItemDecoration(
        new BorderedItemDecoration(this.getResources().getDimensionPixelOffset(R.dimen.default_padding)));
    mRecyclerView.setHasFixedSize(false);

    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayShowTitleEnabled(false);
      getSupportActionBar().setDisplayShowCustomEnabled(true);
      getSupportActionBar().setCustomView(new RoomActionBar(this, mRoomNumber));
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    mRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        int height = mRecyclerView.getHeight();
        mAdapter.setItemHeight(height / 3);
        FirebaseUtils.getRoom(mFirebase, mRoomNumber).addValueEventListener(mRoomValueEventListener);
        mRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
      }
    });

    mRoomValueEventListener = new RoomValueEventListener();

    FirebaseUtils.getPlayer(mFirebase, mRoomNumber, mPlayerId).addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        String name = (String) dataSnapshot.child("name").getValue();
        String monster = (String) dataSnapshot.child("monster").getValue();
        int hp = ((Long) dataSnapshot.child("hp").getValue()).intValue();
        int vp = ((Long) dataSnapshot.child("vp").getValue()).intValue();

        mMainPlayerCard.setName(name);
        mMainPlayerCard.setMonster(monster);
        mMainPlayerCard.setHp(hp);
        mMainPlayerCard.setVp(vp);
      }

      @Override
      public void onCancelled(FirebaseError firebaseError) {
        // TODO(clocksmith)
      }
    });
    FirebaseUtils.getPlayer(mFirebase, mRoomNumber, mPlayerId).onDisconnect().removeValue();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        disconnectAndFinish();
        return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onBackPressed() {
    disconnectAndFinish();
    super.onBackPressed();
  }

  private void disconnectAndFinish() {
    BusProvider.getInstance().unregister(this);
    FirebaseUtils.getPlayer(mFirebase, mRoomNumber, mPlayerId).removeValue();
    FirebaseUtils.getPlayer(mFirebase, mRoomNumber, mPlayerId).onDisconnect().cancel();
    FirebaseUtils.getRoom(mFirebase, mRoomNumber).removeEventListener(mRoomValueEventListener);
    finish();
  }

  private void update(DataSnapshot snapshot) {
    List<Player> players = Lists.newArrayList();
    for (DataSnapshot userSnapshot : snapshot.getChildren()) {
      String name = (String) userSnapshot.child("name").getValue();
      String monster = (String) userSnapshot.child("monster").getValue();
      int hp = ((Long) userSnapshot.child("hp").getValue()).intValue();
      int vp = ((Long) userSnapshot.child("vp").getValue()).intValue();

      Player player = new Player();
      player.name = name;
      player.monster = monster;
      player.hp = hp;
      player.vp = vp;
      players.add(player);
    }
    mAdapter.update(players);
  }

  @Subscribe
  public void on(MainPlayerCard.MainMonsterChangedEvent event) {
    Log.d(TAG, "event: " + event.monster);
    FirebaseUtils.getPlayer(mFirebase, mRoomNumber, mPlayerId).child("monster").setValue(event.monster.getName());
  }

  @Subscribe
  public void on(MainPlayerCard.MainHpChangedEvent event) {
    FirebaseUtils.getPlayer(mFirebase, mRoomNumber, mPlayerId).child("hp").setValue(event.hp);
  }

  @Subscribe
  public void on(MainPlayerCard.MainVpChangedEvent event) {
    FirebaseUtils.getPlayer(mFirebase, mRoomNumber, mPlayerId).child("vp").setValue(event.vp);
  }

  public class BorderedItemDecoration extends RecyclerView.ItemDecoration {
    private int mWidth;

    public BorderedItemDecoration(int width) {
      mWidth = width;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
      int childPosition = parent.getChildPosition(view);
      int numColumns = Constants.NUM_COLUMNS_IN_USER_GRID;
      outRect.left = childPosition % numColumns== 0 ? mWidth : mWidth / 2;
      outRect.right = childPosition % numColumns == numColumns - 1 ? mWidth : mWidth / 2;
      outRect.bottom = mWidth;
      outRect.top = 0;
    }
  }

  private class RoomValueEventListener implements ValueEventListener {
    @Override
    public void onDataChange(DataSnapshot snapshot) {
      Log.d(TAG, "onDataChange()");
      update(snapshot);
    }

    @Override
    public void onCancelled(FirebaseError firebaseError) {
      // TODO(clocksmith)
    }
  }
}
