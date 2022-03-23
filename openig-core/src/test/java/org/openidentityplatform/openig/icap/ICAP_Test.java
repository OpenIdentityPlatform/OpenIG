package org.openidentityplatform.openig.icap;

import java.io.IOException;

import org.forgerock.openig.heap.HeapException;
import org.junit.Test;

import net.rfc3507.client.ICAPClient;
import net.rfc3507.client.ICAPException;
import net.rfc3507.client.ICAPRequest;
import net.rfc3507.client.ICAPRequest.Mode;
import net.rfc3507.client.ICAPResponse;


public class ICAP_Test {

	
	@Test 
	public void test() throws HeapException, IOException {
		try {
			final ICAPClient client=new ICAPClient("localhost", 1344);
			final ICAPRequest request=new ICAPRequest("srv_clamav", Mode.REQMOD);
			request.setHttpRequestBody(new String("X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*").getBytes());
			final ICAPResponse response=client.execute(request);
			System.out.println(new String(response.getHttpRawResponseBody()));
		}catch (ICAPException e) {
//			# start service - after start you can use the java library
//			docker run --rm --name icap-server -p 1344:1344 toolarium/toolarium-icap-calmav-docker:0.0.1
//
//			# optional you can login into the container
//			docker exec -it icap-server /bin/bash
//
//			# the configuration you will see under
//			more /etc/c-icap/c-icap.conf
//
//			# view / tail access-log
//			tail -f /var/log/c-icap/access.log
//
//			# view / tail server-log
//			tail -f /var/log/c-icap/server.log
//
//			# test with c-icap client inside the container
//			c-icap-client -v -f entrypoint.sh -s "srv_clamav" -w 1024 -req http://request -d 5
//
//			# stop service
//			docker stop icap-server
		}
		
	}
	
}
