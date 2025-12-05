import type { PeerSession } from '@/types/messaging';

interface PeerTransport {
  supportsWebRTC: boolean;
  connectPeer: (peerId: string) => Promise<PeerSession>;
  teardown: () => void;
}

export const createPeerTransport = (): PeerTransport => {
  const supportsWebRTC = typeof RTCPeerConnection !== 'undefined';
  const connection = supportsWebRTC
    ? new RTCPeerConnection({
        iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
      })
    : null;

  const connectPeer = async (peerId: string): Promise<PeerSession> => {
    if (!connection) {
      return {
        id: peerId,
        presence: 'connecting',
        lastNegotiatedAt: new Date().toISOString(),
      };
    }

    const offer = await connection.createOffer();
    await connection.setLocalDescription(offer);

    return {
      id: peerId,
      presence: 'online',
      channelHint: offer.sdp ?? 'webrtc-offer',
      lastNegotiatedAt: new Date().toISOString(),
    };
  };

  const teardown = () => {
    connection?.close();
  };

  return { supportsWebRTC, connectPeer, teardown };
};
