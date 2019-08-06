package com.example.gse.imageloader;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity
{
    private RecyclerView mRecyclerView;
    private ProgressBar pDialog;
    private ArrayList<String> mImageList;
    private static final String GET_IMAGE_URL = "https://api.unsplash.com/photos/?client_id=fe4063ed4beedc07c62f05d8daa3a9d6649f2b8d8dd4ada579ba18e6ca4fa453&per_page=30";
    private String[] mPermissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pDialog = findViewById(R.id.progress_bar);

        try {
            // check for read write permission
            if (PermissionsUtility.getInstance(this).checkPermissions(mPermissions))
                getImagesFromServer();
            else
                Toast.makeText(this, "Please provide read/write permission", Toast.LENGTH_SHORT).show();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     *
     */
    private void initialiseView()
    {
        try {
            mRecyclerView = findViewById(R.id.recyclerView);
            mRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
            mRecyclerView.setAdapter(new ListAdapter(mImageList, this));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     *
     */
    private void getImagesFromServer()
    {
        try
        {
            pDialog.setVisibility(View.VISIBLE);

            /**
             * Call for response
             */
            JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(Request.Method.GET, GET_IMAGE_URL, null,
                    new Response.Listener<JSONArray>()
                    {
                        @Override
                        public void onResponse(JSONArray response)
                        {
                            try {
                                pDialog.setVisibility(View.GONE);
                                mImageList = new ArrayList<>();

                                for (int i = 0; i < response.length(); i++)
                                {
                                    JSONObject jsonObject = response.getJSONObject(i);
                                    JSONObject jsonObjectUrls = jsonObject.getJSONObject("urls");
                                    String fullImageUrl = jsonObjectUrls.getString("regular");

                                    mImageList.add(fullImageUrl);
                                }

                                //Load in view
                                initialiseView();
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            pDialog.setVisibility(View.GONE);
                        }
                    });

            /**
             * Put it in volley queue.
             */
            RequestQueue queue = Volley.newRequestQueue(this);
            queue.add(jsonArrayRequest);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     *
     */
    public class ListAdapter extends RecyclerView.Adapter
    {
        private  ArrayList<String> mArrayListImages;
        private Context mContext;

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
        {
            return new ViewHolder(LayoutInflater.from(mContext).inflate(R.layout.item_layout, parent, false));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position)
        {
            ViewHolder viewHolder = (ViewHolder) holder;
            viewHolder.getImageView().setTag(position);
            ImageLoader.with(mContext).load(viewHolder.getImageView(), mArrayListImages.get(position));
        }

        @Override
        public int getItemCount() {
            if(mArrayListImages != null)
                return mArrayListImages.size();
            else
                return 0;
        }

        /**
         *
         * @param items
         * @param context
         */
        public ListAdapter(ArrayList<String> items, Context context)
        {
            super();
            this.mArrayListImages = items;
            this.mContext = context;
        }

        /**
         *
         */
        class ViewHolder extends RecyclerView.ViewHolder
        {
            private final ImageView imageView;
            public final ImageView getImageView() {
                return imageView;
            }

            public ViewHolder(View view) {
                super(view);
                imageView = view.findViewById(R.id.image_view);
                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showCustomFullScreenImageDialog(mArrayListImages.get((int)view.getTag()), mContext);
                    }
                });
            }
        }
    }

    /**
     * @param url
     */
    public void showCustomFullScreenImageDialog(String url, Context mContext)
    {
        try
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

            View view = LayoutInflater.from(mContext).inflate(R.layout.layout_full_screen_image_dialoge, null);
            builder.setView(view);

            ImageView imageView = view.findViewById(R.id.image_view_full_screen);
            ImageButton buttonClose = view.findViewById(R.id.button_close_image);

            ImageLoader.with(mContext).load(imageView, url);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

            final android.app.AlertDialog alertDialog = builder.create();
            alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            alertDialog.show();

            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            alertDialog.getWindow().setAttributes(layoutParams);

            buttonClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (alertDialog != null)
                        alertDialog.dismiss();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        try {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            // check if read write permission given
            if (PermissionsUtility.getInstance(this).isPermissionGiven(mPermissions))
                getImagesFromServer();
            else
                Toast.makeText(this, "Please provide read/write permission", Toast.LENGTH_SHORT).show();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
