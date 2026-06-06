document.getElementById('notifyForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const submitBtn = document.getElementById('submitBtn');
    const spinner = document.getElementById('spinner');
    const btnText = submitBtn.querySelector('span');
    const statusMsg = document.getElementById('statusMessage');

    // UI Loading State
    btnText.classList.add('hidden');
    spinner.classList.remove('hidden');
    submitBtn.disabled = true;
    statusMsg.className = 'status-message';
    statusMsg.style.display = 'none';

    // Build Payload
    const requestData = {
        recipient: document.getElementById('recipient').value,
        templateId: document.getElementById('templateId').value,
        payload: {
            name: document.getElementById('payloadName').value,
            txId: document.getElementById('payloadTxId').value,
            otp: document.getElementById('payloadOtp').value
        }
    };

    try {
        const response = await fetch('/api/notifications', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                // Generate a random user ID for testing rate limiting if needed
                'X-User-Id': 'demo-user-123' 
            },
            body: JSON.stringify(requestData)
        });

        if (response.status === 202) {
            statusMsg.textContent = '⚡ Event Dispatched to Message Queue Successfully!';
            statusMsg.className = 'status-message success';
            // Optional: reset form
            // document.getElementById('notifyForm').reset();
        } else if (response.status === 429) {
            statusMsg.textContent = '⚠️ Too Many Requests! Rate limit exceeded (Max 5/min).';
            statusMsg.className = 'status-message error';
        } else {
            statusMsg.textContent = '❌ Failed to dispatch event. Server returned ' + response.status;
            statusMsg.className = 'status-message error';
        }
    } catch (error) {
        statusMsg.textContent = '❌ Network Error: Could not connect to NotifyX Engine.';
        statusMsg.className = 'status-message error';
    } finally {
        // Reset UI State
        btnText.classList.remove('hidden');
        spinner.classList.add('hidden');
        submitBtn.disabled = false;
        statusMsg.style.display = 'block';
    }
});
