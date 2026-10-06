(function () {
    function sendHeartbeat() {
        fetch('/api/stats/heartbeat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        }).catch(err => console.debug("Heartbeat error:", err));
    }

    // Send immediately on load
    sendHeartbeat();

    // Send heartbeat every 2 minutes (matches server-side 5 min timeout comfortably)
    setInterval(sendHeartbeat, 120000);
})();
