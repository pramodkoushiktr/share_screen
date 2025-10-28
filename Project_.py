import socket
import struct
import cv2
import numpy as np

def receive_all(sock, size):
    """Receive exactly 'size' bytes from a socket."""
    data = bytearray()
    while len(data) < size:
        packet = sock.recv(size - len(data))
        if not packet:
            return None
        data.extend(packet)
    return data

def main():
    host = "127.0.0.1"
    port = 54322

    # Create server socket
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.bind((host, port))
    server_socket.listen(1)
    print(f"Listening on {host}:{port}...")

    conn, addr = server_socket.accept()
    print(f"Connected from: {addr}")

    # Create resizable window
    cv2.namedWindow("Screen Share", cv2.WINDOW_NORMAL)

    # Detect your desktop resolution dynamically
    import screeninfo
    screen = screeninfo.get_monitors()[0]
    screen_w, screen_h = screen.width, screen.height
    print(f"Desktop resolution: {screen_w}x{screen_h}")

    try:
        while True:
            # Read 4-byte frame size
            size_data = receive_all(conn, 4)
            if not size_data:
                break
            (frame_size,) = struct.unpack(">I", size_data)

            # Read the JPEG frame
            frame_data = receive_all(conn, frame_size)
            if not frame_data:
                break

            # Decode JPEG
            np_array = np.frombuffer(frame_data, np.uint8)
            img = cv2.imdecode(np_array, cv2.IMREAD_COLOR)

            if img is not None:
                h, w = img.shape[:2]

                # Keep the aspect ratio same as phone
                scale = min(screen_w / w, screen_h / h)
                new_w, new_h = int(w * scale), int(h * scale)

                # Resize image to fit desktop window
                resized = cv2.resize(img, (new_w, new_h), interpolation=cv2.INTER_AREA)

                # Create a black canvas of desktop size (optional – keeps it centered)
                canvas = np.zeros((screen_h, screen_w, 3), dtype=np.uint8)
                x_offset = (screen_w - new_w) // 2
                y_offset = (screen_h - new_h) // 2
                canvas[y_offset:y_offset+new_h, x_offset:x_offset+new_w] = resized

                cv2.imshow("Screen Share", canvas)

                if cv2.waitKey(1) & 0xFF == ord('q'):
                    break
            else:
                print("Could not decode frame.")
    finally:
        print("Closing connection...")
        conn.close()
        server_socket.close()
        cv2.destroyAllWindows()

if __name__ == "__main__":
    main()
