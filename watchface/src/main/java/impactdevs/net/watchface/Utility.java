package impactdevs.net.watchface;

/**
 * Created by Ian on 5/16/2016.
 */
public class Utility {

    //match weatherID to weather image
    public static int getBackgroundForWeatherID(int weatherId) {
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.rain;
        } else if (weatherId == 511) {
            return R.drawable.snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.fog;
        } else if (weatherId == 761 || weatherId == 781) {
            return R.drawable.storm;
        } else if (weatherId == 800) {
            return R.drawable.clear;
        } else if (weatherId == 801) {
            return R.drawable.light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.cloud;
        }
        return -1;
    }

}
