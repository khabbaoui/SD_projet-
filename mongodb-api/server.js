app.post('/query', async (req, res) => {
  try {
    console.log('Incoming query:', JSON.stringify(req.body, null, 2));

    if (!db) throw new Error('Database not connected');

    const query = req.body?.targets?.[0]?.payload || {};
    const data = await db.collection('sensor_readings')
      .find(query)
      .sort({ timestamp: 1 })
      .limit(1000) // Prevent overload
      .toArray();

    res.json([{
      target: "capteur-01",
      datapoints: data.map(item => ([
        item.value.consommation_L_min,
        new Date(item.timestamp).getTime()
      ]))
    }]);

  } catch (err) {
    console.error('Error:', err);
    res.status(500).json({
      error: err.message,
      stack: process.env.NODE_ENV === 'development' ? err.stack : undefined
    });
  }
});