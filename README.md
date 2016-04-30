# bustracker-android
A simple Android service to post frequent fine location updates to a HTTP API. Does not attempt to conserve battery or bandwidth at the moment.

## Usage

This assumes you're using AWS's API Gateway service to accept POST requests containing the following JSON body:
```javascript
{
  "data": {
    "bus_id": "string", // configurable in the app, defaults to "test"
    "latitude": float,
    "longitude": float,
    "timestamp": "string" // Unix timestamp
  }
}
```

You can specify the API Gateway URL to send requests to, as well as the API key to use in [`TrackerService.java`](https://github.com/scascketta/bustracker-android/blob/master/app/src/main/java/com/scascketta/bustracker/TrackerService.java).
