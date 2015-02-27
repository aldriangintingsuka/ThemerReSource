package com.gfamily.resource.themer.Business.Managers;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;

import com.google.gson.Gson;

public class IconPackManager implements IIconPackManager
{
  private PackageManager _pm;
  private Gson _jsonParser;
  private SharedPreferences _preference;
  private Activity _activity;

  public IconPackManager( PackageManager pm, Gson jsonParser, SharedPreferences preference, Activity activity )
  {
    _pm = pm;
    _jsonParser = jsonParser;
    _preference = preference;
    _activity = activity;
  }

  @Override
  @JavascriptInterface
  public String GetIconPacks()
  {
    Log.d( "gfamily", "get icon pack" );
    Intent mainIntent = new Intent( Intent.ACTION_MAIN, null );
    mainIntent.addCategory( "com.anddoes.launcher.THEME" );
    List<ResolveInfo> resolveInfos = _pm.queryIntentActivities( mainIntent, 0 );

    ArrayList<IconPack> iconPacks = new ArrayList<IconPack>();

    for( ResolveInfo resolveInfo : resolveInfos )
    {
      String packageName = resolveInfo.activityInfo.packageName;
      String packageLabel = resolveInfo.activityInfo.applicationInfo.loadLabel( _pm ).toString();
      Bitmap packageIcon = ( (BitmapDrawable) resolveInfo.activityInfo.applicationInfo.loadIcon( _pm ) ).getBitmap();
      packageIcon.setDensity( 480 );
      IconPack iconPack = new IconPack( packageName, packageLabel, packageIcon );
      iconPacks.add( iconPack );
    }

    String currentIconPack = _preference.getString( "currentIconPack", "" );
    
    HashMap<String,Object> resultMap = new HashMap<String, Object>();
    resultMap.put( "iconPacks", iconPacks );
    resultMap.put( "currentIconPack", currentIconPack );
    
    String result = _jsonParser.toJson( resultMap );
    Log.d( "gfamily", "current icon pack " + currentIconPack );

    return result;
  }

  @Override
  @JavascriptInterface
  public String SetIconPack( String iconPackPackageName )
  {
    Log.d( "gfamily", "set icon pack " + iconPackPackageName );
    HashMap<String, HashMap<String, String>> drawableMap = new HashMap<String, HashMap<String, String>>();

    try
    {
      boolean isFromResources = GetDrawableMapFromResources( iconPackPackageName, drawableMap );

      if( !isFromResources ) GetDrawableMapFromAssets( iconPackPackageName, drawableMap );
      
      SharedPreferences.Editor edit = _preference.edit();
      edit.putString( "currentIconPack", iconPackPackageName );
      edit.commit();

      WriteScriptFile( drawableMap, iconPackPackageName );
      
      Intent intent = new Intent( "com.gfamily.resource.UPDATE_SCRIPT" );
      intent.addCategory( Intent.CATEGORY_DEFAULT );
      intent.putExtra( "content", "icon theme" );
      _activity.startService( intent );
    }
    catch( Exception e )
    {
    }

    return null;
  }

  private boolean GetDrawableMapFromResources( String iconPackPackageName, HashMap<String, HashMap<String, String>> drawableMap )
  {
    try
    {
      Resources themeResources = _pm.getResourcesForApplication( iconPackPackageName );
      int xmlID = themeResources.getIdentifier( "appfilter", "xml", iconPackPackageName );

      if( xmlID == 0 ) return false;

      XmlResourceParser xmlParser = themeResources.getXml( xmlID );

      ParseXml( drawableMap, xmlParser );

      // indicate app done reading the resource.
      xmlParser.close();
    }
    catch( Exception e )
    {
      e.printStackTrace();
      return false;
    }

    return true;
  }

  private void GetDrawableMapFromAssets( String iconPackPackageName, HashMap<String, HashMap<String, String>> drawableMap )
  {
    try
    {
      Resources themeResources = _pm.getResourcesForApplication( iconPackPackageName );
      AssetManager assetManager = themeResources.getAssets();
      InputStream inputStream = assetManager.open( "appfilter.xml" );

      XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
      factory.setNamespaceAware( true );
      XmlPullParser xmlParser = factory.newPullParser();

      xmlParser.setInput( inputStream, null );
      ParseXml( drawableMap, xmlParser );
    }
    catch( Exception e )
    {
      e.printStackTrace();
    }
  }

  private void ParseXml( HashMap<String, HashMap<String, String>> drawableMap, XmlPullParser xmlParser ) throws XmlPullParserException, IOException
  {
    int eventType = xmlParser.getEventType();

    while( eventType != XmlPullParser.END_DOCUMENT )
    {
      if( eventType == XmlPullParser.START_TAG && xmlParser.getName().equals( "item" ) )
      {
        String componentText = xmlParser.getAttributeValue( null, "component" );
        String drawableName = xmlParser.getAttributeValue( null, "drawable" );

        Pattern pattern = Pattern.compile( "ComponentInfo\\{(.+)/(.+)\\}" );
        Matcher matcher = pattern.matcher( componentText );

        String packageName = "";
        String activityName = "";

        if( matcher.find() )
        {
          packageName = matcher.group( 1 );
          activityName = matcher.group( 2 );
        }

        if( !drawableMap.containsKey( packageName ) ) drawableMap.put( packageName, new HashMap<String, String>() );

        HashMap<String, String> activityMap = drawableMap.get( packageName );
        activityMap.put( activityName, drawableName );
      }

      eventType = xmlParser.next();
    }
  }

  private void WriteScriptFile( HashMap<String, HashMap<String, String>> drawableMap, String themePackageName )
  {
    BufferedWriter bw = null;
    File scriptFile = new File( "/sdcard/com.gfamily.resource/Mods/IconThemer.txt" );

    try
    {
      bw = new BufferedWriter( new FileWriter( scriptFile ) );
      bw.write( "setBaseForwardPackage " + " " + themePackageName + "\n" );

      List<PackageInfo> packageInfos = _pm.getInstalledPackages( PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES );

      for( PackageInfo packageInfo : packageInfos )
      {
        String packageName = packageInfo.packageName;

        String appIconName = "";
        String appResourceType = "";
        String iconName;
        String resourceType;

        try
        {
          Resources resourcesForApplication = _pm.getResourcesForApplication( packageName );
          Map<String, Boolean> activityIconNameMap = new HashMap<String, Boolean>();
          int appIconResource = packageInfo.applicationInfo.icon;

          if( appIconResource > 0 )
          {
            appIconName = resourcesForApplication.getResourceEntryName( appIconResource );
            appResourceType = resourcesForApplication.getResourceTypeName( appIconResource );

            activityIconNameMap.put( appIconName, false );
            bw.write( "beginReplace " + packageName + " " + appResourceType + "\n" );
            bw.write( "withMaskResource " + appIconName + " " + "iconback" + "\n" );
          }

          if( packageInfo.activities == null || packageInfo.activities.length == 0 )
          {
          }
          else
          {
            for( ActivityInfo activityInfo : packageInfo.activities )
            {
              String activityName = activityInfo.name;

              try
              {
                int iconResource = activityInfo.getIconResource();

                if( iconResource > 0 )
                {
                  iconName = resourcesForApplication.getResourceEntryName( iconResource );
                  resourceType = resourcesForApplication.getResourceTypeName( iconResource );

                  String drawableName = drawableMap.containsKey( packageName ) && drawableMap.get( packageName ).containsKey( activityName ) ? drawableMap.get( packageName ).get( activityName ) : null;

                  if( !activityIconNameMap.containsKey( iconName ) || !activityIconNameMap.get( iconName ) )
                  {
                    if( !activityIconNameMap.containsKey( iconName ) || !activityIconNameMap.get( iconName ) && drawableName != null )
                      bw.write( "beginReplace " + packageName + " " + resourceType  + "\n" );

                    if( drawableName != null )
                    {
                      activityIconNameMap.put( iconName, true );
                      bw.write( "withResource " + iconName + " " + drawableName + "\n" );
                    }
                    else if( !activityIconNameMap.containsKey( iconName ) )
                    {
                      activityIconNameMap.put( iconName, false );
                      bw.write( "withMaskResource " + iconName + " " + "iconback" + "\n" );
                    }
                  }
                }
              }
              catch( Exception e )
              {
                e.printStackTrace();
              }
            }
          }
        }
        catch( Exception e )
        {
          e.printStackTrace();
        }
      }
    }
    catch( Exception e )
    {
      e.printStackTrace();
    }
    finally
    {
      if( bw != null ) try
      {
        bw.close();
      }
      catch( IOException e )
      {
        e.printStackTrace();
      }
    }
  }

  private class IconPack
  {
    public String PackageName;
    public String PackageLabel;
    public String PackageIconString;

    public IconPack( String packageName, String packageLabel, Bitmap packageIcon )
    {
      PackageName = packageName;
      PackageLabel = packageLabel;

      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      packageIcon.compress( Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream );
      byte[] byteArray = byteArrayOutputStream.toByteArray();
      String imageBase64 = Base64.encodeToString( byteArray, Base64.DEFAULT );
      PackageIconString = "data:image/png;base64," + imageBase64;
    }
  }
}
