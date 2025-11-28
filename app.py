from flask import Flask, request, jsonify
from flask import render_template
from datetime import datetime

app = Flask(__name__)



rescue_centers = [
    {
        "id": "A",
        "name": "Gas Station Team",
        "lat": 13.780211,
        "lon": 121.037033
    },
    {
        "id": "B",
        "name": "Grocery Store Team",
        "lat": 13.779400,
        "lon": 121.038707
    },
    {
        "id": "C",
        "name": "Coffee Shop Team",
        "lat": 13.780796,
        "lon": 121.035210
    }

]

crash_logs = {}



@app.route("/team/<center>")
def center_screen_log(center):
    return render_template("center.html", team_id=center)


@app.route("/", methods=["GET"])
def home():
    return jsonify({"message": "ImpactAlert Server is running"})


@app.route("/crash", methods=["POST"])
def crash_report():
    global crash_logs
    data = request.get_json(force=True)
    if not data or data.get("lat") is None or data.get("lon") is None:
        return jsonify({"error": "lat/lon missing"}), 400

    driver_lat = data["lat"]
    driver_lon = data["lon"]

    # find nearest center
    nearest = min(rescue_centers,
                  key=lambda c: ((driver_lat - c["lat"])**2 + (driver_lon - c["lon"])**2)**0.5)

    # prepare crash info
    crash_info = {
        "driver_lat": driver_lat,
        "driver_lon": driver_lon,   
        "nearest_center": nearest["name"],
        "center_id": nearest["id"],
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
}

    # store crash for the **nearest client only**
    if nearest["id"] not in crash_logs:
        crash_logs[nearest["id"]] = []     
            
    crash_logs[nearest["id"]].append(crash_info)

    return jsonify({
        "status": "received",
        "nearest_center": nearest["name"],
        "center_id": nearest["id"]
    }), 200


@app.route("/crash_log/<client_id>", methods=["GET"])
def get_crash_log(client_id):
    logs = crash_logs.get(client_id, [])
    return jsonify(logs)


# VIEW ALL RESCUE CENTERS
@app.route("/centers", methods=["GET"])
def get_centers():
    return jsonify(rescue_centers)


def distance(lat1, lon1, lat2, lon2):
    return ((lat1 - lat2)**2 + (lon1 - lon2)**2) ** 0.5

@app.route("/driver_tracking")
def driver_interface():
    return app.send_static_file("driver.html")


if __name__ == "__main__":
    app.run(debug=True)