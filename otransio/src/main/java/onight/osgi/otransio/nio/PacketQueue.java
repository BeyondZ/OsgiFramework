package onight.osgi.otransio.nio;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.glassfish.grizzly.impl.FutureImpl;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.ck.CKConnPool;
import onight.tfw.async.CompleteHandler;
import onight.tfw.otransio.api.beans.FramePacket;

@Slf4j
@Data
public class PacketQueue {

	ConcurrentLinkedQueue<PacketWriteTask> queue = new ConcurrentLinkedQueue<>();
	long lastUpdatedMS = System.currentTimeMillis();

	PacketWriteWorker writer;

	boolean isStop = false;

	CKConnPool ckpool;

	public PacketQueue(CKConnPool ckpool, int max_packet_buffer, int writer_thread_count) {
		this.ckpool = ckpool;
		writer = new PacketWriteWorker(this, max_packet_buffer);
		for (int i = 0; i < writer_thread_count; i++) {
			new Thread(writer).start();
		}
	}

	public void offer(FramePacket fp,final CompleteHandler handler,FutureImpl<FramePacket> future) {
		queue.offer(new PacketWriteTask(fp,handler,false,future));
		try {
			synchronized (writer) {
				writer.notifyAll();
			}
		} catch (Exception e) {
			log.warn("error:" + e.getMessage(), e);
		}
	}

	public PacketWriteTask poll() {
		return queue.poll();
	}
}
