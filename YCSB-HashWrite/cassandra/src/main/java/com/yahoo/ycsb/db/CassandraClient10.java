/**
 * Copyright (c) 2010 Yahoo! Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package com.yahoo.ycsb.db;

import com.yahoo.ycsb.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;
import java.util.Random;
import java.util.Properties;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.zookeeper.KeeperException;
import org.apache.cassandra.thrift.*;


//XXXX if we do replication, fix the consistency levels
/**
 * Cassandra 1.0.6 client for YCSB framework
 */
public class CassandraClient10 extends DB
{
  static Random random = new Random();
  public static final int Ok = 0;
  public static final int Error = -1;
  public static final ByteBuffer emptyByteBuffer = ByteBuffer.wrap(new byte[0]);

  public static int ConnectionRetries;
  public int OperationRetries;
  public String column_family;
  private static Boolean running=true;
  private static String ringState=""; //109.231.85.43<>-3074457345618258400|109.231.85.85<>0|

  public static final String CONNECTION_RETRY_PROPERTY = "cassandra.connectionretries";
  public static final String CONNECTION_RETRY_PROPERTY_DEFAULT = "300";

  public static final String OPERATION_RETRY_PROPERTY = "cassandra.operationretries";
  public static final String OPERATION_RETRY_PROPERTY_DEFAULT = "300";

  public static final String USERNAME_PROPERTY = "cassandra.username";
  public static final String PASSWORD_PROPERTY = "cassandra.password";

  public static final String COLUMN_FAMILY_PROPERTY = "cassandra.columnfamily";
  public static final String COLUMN_FAMILY_PROPERTY_DEFAULT = "data";
 
  public static final String READ_CONSISTENCY_LEVEL_PROPERTY = "cassandra.readconsistencylevel";
  public static final String READ_CONSISTENCY_LEVEL_PROPERTY_DEFAULT = "ONE";

  public static final String WRITE_CONSISTENCY_LEVEL_PROPERTY = "cassandra.writeconsistencylevel";
  public static final String WRITE_CONSISTENCY_LEVEL_PROPERTY_DEFAULT = "ONE";

  public static final String SCAN_CONSISTENCY_LEVEL_PROPERTY = "cassandra.scanconsistencylevel";
  public static final String SCAN_CONSISTENCY_LEVEL_PROPERTY_DEFAULT = "ONE";

  public static final String DELETE_CONSISTENCY_LEVEL_PROPERTY = "cassandra.deleteconsistencylevel";
  public static final String DELETE_CONSISTENCY_LEVEL_PROPERTY_DEFAULT = "ONE";


  static TTransport tr;
  static CassandraClient10 Instance;
  static Cassandra.Client client;
   static String myhost;
 // Cassandra.Client [] array;

  boolean _debug = false;

  String _table = "";
  Exception errorexception = null;

  List<Mutation> mutations = new ArrayList<Mutation>();
  Map<String, List<Mutation>> mutationMap = new HashMap<String, List<Mutation>>();
  Map<ByteBuffer, Map<String, List<Mutation>>> record = new HashMap<ByteBuffer, Map<String, List<Mutation>>>();

  ColumnParent parent;
 
  ConsistencyLevel readConsistencyLevel = ConsistencyLevel.ONE;
  ConsistencyLevel writeConsistencyLevel = ConsistencyLevel.ONE;
  ConsistencyLevel scanConsistencyLevel = ConsistencyLevel.ONE;
  ConsistencyLevel deleteConsistencyLevel = ConsistencyLevel.ONE;


 
  public static Charset charset = Charset.forName("UTF-8");
    public static CharsetEncoder encoder = charset.newEncoder();
    public static CharsetDecoder decoder = charset.newDecoder();   
    
    
    private static long normalize(long v)
    {
        // We exclude the MINIMUM value; see getToken()
        return v == Long.MIN_VALUE ? Long.MAX_VALUE : v;
    }
    public static String bb_to_str(ByteBuffer buffer){
    String data = "";
    try{
        int old_position = buffer.position();
        data = decoder.decode(buffer).toString();
        // reset buffer's position to its original so it is not altered:
        buffer.position(old_position);  
    }catch (Exception e){
      e.printStackTrace();
      return "";
    }
    return data;
    }
    
    
    public static void pgarefinit(String newhost) {
        Exception connectexception = null;
        System.out.println("PG Mpika change");
        if(myhost.compareTo(newhost) == 0 ){
        	System.out.println("Do nothing!");
        }
        else{
        	System.out.println("Do something!");
        	for (int retry = 0; retry < ConnectionRetries; retry++)
            {
              tr = new TFramedTransport(new TSocket(myhost, 9160));
              TProtocol proto = new TBinaryProtocol(CassandraClient10.Instance.tr);
              client = new Cassandra.Client(proto);
              try
              {
            	tr.open();
                connectexception = null;
                break;
              } catch (Exception e)
              {
                connectexception = e;
              }
              try
              {
                Thread.sleep(1000);
              } catch (InterruptedException e)
              {
              }
            }
            if (connectexception != null)
            {
              System.err.println("Unable to connect to " + myhost + " after " + ConnectionRetries
                  + " tries");
            }
            try
            {
            	client.set_keyspace("usertable");
            }
            catch (Exception e)
            {
              e.printStackTrace();
              e.printStackTrace(System.out);
            }
        }
        
    	
    }
    
  /**
   * Initialize any state for this DB. Called once per DB instance; there is one
   * DB instance per client thread.
   */
  public void init() throws DBException
  {
    
  	
    String hosts = getProperties().getProperty("hosts");
    if (hosts == null)
    {
      throw new DBException("Required property \"hosts\" missing for CassandraClient");
    }

    column_family = getProperties().getProperty(COLUMN_FAMILY_PROPERTY, COLUMN_FAMILY_PROPERTY_DEFAULT);
    parent = new ColumnParent(column_family);

    ConnectionRetries = Integer.parseInt(getProperties().getProperty(CONNECTION_RETRY_PROPERTY,
        CONNECTION_RETRY_PROPERTY_DEFAULT));
    OperationRetries = Integer.parseInt(getProperties().getProperty(OPERATION_RETRY_PROPERTY,
        OPERATION_RETRY_PROPERTY_DEFAULT));

    String username = getProperties().getProperty(USERNAME_PROPERTY);
    String password = getProperties().getProperty(PASSWORD_PROPERTY);

    readConsistencyLevel = ConsistencyLevel.valueOf(getProperties().getProperty(READ_CONSISTENCY_LEVEL_PROPERTY, READ_CONSISTENCY_LEVEL_PROPERTY_DEFAULT));
    writeConsistencyLevel = ConsistencyLevel.valueOf(getProperties().getProperty(WRITE_CONSISTENCY_LEVEL_PROPERTY, WRITE_CONSISTENCY_LEVEL_PROPERTY_DEFAULT));
    scanConsistencyLevel = ConsistencyLevel.valueOf(getProperties().getProperty(SCAN_CONSISTENCY_LEVEL_PROPERTY, SCAN_CONSISTENCY_LEVEL_PROPERTY_DEFAULT));
    deleteConsistencyLevel = ConsistencyLevel.valueOf(getProperties().getProperty(DELETE_CONSISTENCY_LEVEL_PROPERTY, DELETE_CONSISTENCY_LEVEL_PROPERTY_DEFAULT));


    _debug = Boolean.parseBoolean(getProperties().getProperty("debug", "false"));

    
   // array = new Cassandra.Client[4];
   
   
    String hh = "109.231.85.83";
    String[] allhosts = hh.split(",");
    
   // for(int i= 0 ; i < allhosts.length ; i++){
   // String myhost = allhosts[0];
    myhost = hh;
    
    Exception connectexception = null;

    for (int retry = 0; retry < ConnectionRetries; retry++)
    {
      tr = new TFramedTransport(new TSocket(myhost, 9160));
      TProtocol proto = new TBinaryProtocol(tr);
      client = new Cassandra.Client(proto);
      try
      {
        tr.open();
        connectexception = null;
        break;
      } catch (Exception e)
      {
        connectexception = e;
      }
      try
      {
        Thread.sleep(1000);
      } catch (InterruptedException e)
      {
      }
    }
    if (connectexception != null)
    {
      System.err.println("Unable to connect to " + myhost + " after " + ConnectionRetries
          + " tries");
      throw new DBException(connectexception);
    }
    Instance = this;
  //  array[i] = client;
    //enddddd
   // }
    
    
    
    /*-------------------------patch-------------------- */	
    Thread t = new Thread() {
        public void run() {
        	try {
				new Executor("109.231.85.43:2181", "/cazooMaster").run();
			} catch (KeeperException e) {
				System.out.println("pgaref KeeperException");
				e.printStackTrace();
			} catch (IOException e) {
				System.out.println("pgaref IOException");
				e.printStackTrace();
			}
        }
    };
    t.start();
    
   /*-------------------------------------------------------------*/ 	
  	
    
    
    
  }
  protected static long getblock(ByteBuffer key, int offset, int index)
    {
        int i_8 = index << 3;
        int blockOffset = offset + i_8;
        return ((long) key.get(blockOffset + 0) & 0xff) + (((long) key.get(blockOffset + 1) & 0xff) << 8) +
               (((long) key.get(blockOffset + 2) & 0xff) << 16) + (((long) key.get(blockOffset + 3) & 0xff) << 24) +
               (((long) key.get(blockOffset + 4) & 0xff) << 32) + (((long) key.get(blockOffset + 5) & 0xff) << 40) +
               (((long) key.get(blockOffset + 6) & 0xff) << 48) + (((long) key.get(blockOffset + 7) & 0xff) << 56);
    }

    protected static long rotl64(long v, int n)
    {
        return ((v << n) | (v >>> (64 - n)));
    }

    protected static long fmix(long k)
    {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;

        return k;
    }

    public static long[] hash3_x64_128(ByteBuffer key, int offset, int length, long seed)
    {
        final int nblocks = length >> 4; // Process as 128-bit blocks.

        long h1 = seed;
        long h2 = seed;

        long c1 = 0x87c37b91114253d5L;
        long c2 = 0x4cf5ad432745937fL;

        //----------
        // body

        for(int i = 0; i < nblocks; i++)
        {
            long k1 = getblock(key, offset, i*2+0);
            long k2 = getblock(key, offset, i*2+1);

            k1 *= c1; k1 = rotl64(k1,31); k1 *= c2; h1 ^= k1;

            h1 = rotl64(h1,27); h1 += h2; h1 = h1*5+0x52dce729;

            k2 *= c2; k2  = rotl64(k2,33); k2 *= c1; h2 ^= k2;

            h2 = rotl64(h2,31); h2 += h1; h2 = h2*5+0x38495ab5;
        }

        //----------
        // tail

        // Advance offset to the unprocessed tail of the data.
        offset += nblocks * 16;

        long k1 = 0;
        long k2 = 0;

        switch(length & 15)
        {
            case 15: k2 ^= ((long) key.get(offset+14)) << 48;
            case 14: k2 ^= ((long) key.get(offset+13)) << 40;
            case 13: k2 ^= ((long) key.get(offset+12)) << 32;
            case 12: k2 ^= ((long) key.get(offset+11)) << 24;
            case 11: k2 ^= ((long) key.get(offset+10)) << 16;
            case 10: k2 ^= ((long) key.get(offset+9)) << 8;
            case  9: k2 ^= ((long) key.get(offset+8)) << 0;
                k2 *= c2; k2  = rotl64(k2,33); k2 *= c1; h2 ^= k2;

            case  8: k1 ^= ((long) key.get(offset+7)) << 56;
            case  7: k1 ^= ((long) key.get(offset+6)) << 48;
            case  6: k1 ^= ((long) key.get(offset+5)) << 40;
            case  5: k1 ^= ((long) key.get(offset+4)) << 32;
            case  4: k1 ^= ((long) key.get(offset+3)) << 24;
            case  3: k1 ^= ((long) key.get(offset+2)) << 16;
            case  2: k1 ^= ((long) key.get(offset+1)) << 8;
            case  1: k1 ^= ((long) key.get(offset));
                k1 *= c1; k1  = rotl64(k1,31); k1 *= c2; h1 ^= k1;
        };

        //----------
        // finalization

        h1 ^= length; h2 ^= length;

        h1 += h2;
        h2 += h1;

        h1 = fmix(h1);
        h2 = fmix(h2);

        h1 += h2;
        h2 += h1;

        return(new long[] {h1, h2});
    }

  /**
   * Cleanup any state for this DB. Called once per DB instance; there is one DB
   * instance per client thread.
   */
  public void cleanup() throws DBException
  {
    tr.close();
  }

  /**
   * Read a record from the database. Each field/value pair from the result will
   * be stored in a HashMap.
   *
   * @param table
   *          The name of the table
   * @param key
   *          The record key of the record to read.
   * @param fields
   *          The list of fields to read, or null for all of them
   * @param result
   *          A HashMap of field/value pairs for the result
   * @return Zero on success, a non-zero error code on error
   */
  public int read(String table, String key, Set<String> fields, HashMap<String, ByteIterator> result)
  {
      /* CONSISTENCY PART
       * pgaref & panapan
       * ICS-FORTH
       * Spring 2013
       
      ByteBuffer keyz = ByteBuffer.wrap(key.getBytes());
      long hash = hash3_x64_128(keyz, keyz.position(), keyz.remaining(), 0)[0];
      long norm = normalize(hash);
      long [] ring = new long[4];
      
      ring[0] = 0x0;
      ring[1] = 0xD72E294260C19D86L;
      ring[2]= 0xAE5C5284C1833B0CL;
      ring[3]= 0x9705873672A62BCBL;
      
      
      if((norm <=  ring[0]) && (norm >= ring[3])){
          client = array[0];
      }
      else if((norm <=  ring[1]) && (norm >= ring[0])){
          client= array[2];
      }
      else if((norm <=  ring[2]) && (norm >= ring[1])){
          client = array[1];
      }
      else{
          client = array[3];
      }*/
     
    if (!_table.equals(table)) {
      try
      {
        client.set_keyspace(table);
        _table = table;
      }
      catch (Exception e)
      {
        e.printStackTrace();
        e.printStackTrace(System.out);
        return Error;
      }
    }

    for (int i = 0; i < OperationRetries; i++)
    {

      try
      {
        SlicePredicate predicate;
        if (fields == null)
        {
          predicate = new SlicePredicate().setSlice_range(new SliceRange(emptyByteBuffer, emptyByteBuffer, false, 1000000));

        } else {
          ArrayList<ByteBuffer> fieldlist = new ArrayList<ByteBuffer>(fields.size());
          for (String s : fields)
          {
            fieldlist.add(ByteBuffer.wrap(s.getBytes("UTF-8")));
          }

          predicate = new SlicePredicate().setColumn_names(fieldlist);
        }

        List<ColumnOrSuperColumn> results = client.get_slice(ByteBuffer.wrap(key.getBytes("UTF-8")), parent, predicate, readConsistencyLevel);

        if (_debug)
        {
          System.out.print("Reading key: " + key);
        }

        Column column;
        String name;
        ByteIterator value;
        for (ColumnOrSuperColumn oneresult : results)
        {

          column = oneresult.column;
            name = new String(column.name.array(), column.name.position()+column.name.arrayOffset(), column.name.remaining());
            value = new ByteArrayByteIterator(column.value.array(), column.value.position()+column.value.arrayOffset(), column.value.remaining());

          result.put(name,value);

          if (_debug)
          {
            System.out.print("(" + name + "=" + value + ")");
          }
        }

        if (_debug)
        {
          System.out.println();
          System.out.println("ConsistencyLevel=" + readConsistencyLevel.toString());
        }

        return Ok;
      } catch (Exception e)
      {
        errorexception = e;
      }

      try
      {
        Thread.sleep(500);
      } catch (InterruptedException e)
      {
      }
    }
    errorexception.printStackTrace();
    errorexception.printStackTrace(System.out);
    return Error;

  }

  /**
   * Perform a range scan for a set of records in the database. Each field/value
   * pair from the result will be stored in a HashMap.
   *
   * @param table
   *          The name of the table
   * @param startkey
   *          The record key of the first record to read.
   * @param recordcount
   *          The number of records to read
   * @param fields
   *          The list of fields to read, or null for all of them
   * @param result
   *          A Vector of HashMaps, where each HashMap is a set field/value
   *          pairs for one record
   * @return Zero on success, a non-zero error code on error
   */
  public int scan(String table, String startkey, int recordcount, Set<String> fields,
      Vector<HashMap<String, ByteIterator>> result)
  {
    if (!_table.equals(table)) {
      try
      {
        client.set_keyspace(table);
        _table = table;
      }
      catch (Exception e)
      {
        e.printStackTrace();
        e.printStackTrace(System.out);
        return Error;
      }
    }

    for (int i = 0; i < OperationRetries; i++)
    {

      try
      {
        SlicePredicate predicate;
        if (fields == null)
        {
          predicate = new SlicePredicate().setSlice_range(new SliceRange(emptyByteBuffer, emptyByteBuffer, false, 1000000));

        } else {
          ArrayList<ByteBuffer> fieldlist = new ArrayList<ByteBuffer>(fields.size());
          for (String s : fields)
          {
              fieldlist.add(ByteBuffer.wrap(s.getBytes("UTF-8")));
          }

          predicate = new SlicePredicate().setColumn_names(fieldlist);
        }

        KeyRange kr = new KeyRange().setStart_key(startkey.getBytes("UTF-8")).setEnd_key(new byte[] {}).setCount(recordcount);

        List<KeySlice> results = client.get_range_slices(parent, predicate, kr, scanConsistencyLevel);

        if (_debug)
        {
          System.out.println("Scanning startkey: " + startkey);
        }

        HashMap<String, ByteIterator> tuple;
        for (KeySlice oneresult : results)
        {
          tuple = new HashMap<String, ByteIterator>();

          Column column;
          String name;
          ByteIterator value;
          for (ColumnOrSuperColumn onecol : oneresult.columns)
          {
              column = onecol.column;
              name = new String(column.name.array(), column.name.position()+column.name.arrayOffset(), column.name.remaining());
              value = new ByteArrayByteIterator(column.value.array(), column.value.position()+column.value.arrayOffset(), column.value.remaining());

              tuple.put(name, value);

            if (_debug)
            {
              System.out.print("(" + name + "=" + value + ")");
            }
          }

          result.add(tuple);
          if (_debug)
          {
            System.out.println();
            System.out.println("ConsistencyLevel=" + scanConsistencyLevel.toString());
          }
        }

        return Ok;
      } catch (Exception e)
      {
        errorexception = e;
      }
      try
      {
        Thread.sleep(500);
      } catch (InterruptedException e)
      {
      }
    }
    errorexception.printStackTrace();
    errorexception.printStackTrace(System.out);
    return Error;
  }

  /**
   * Update a record in the database. Any field/value pairs in the specified
   * values HashMap will be written into the record with the specified record
   * key, overwriting any existing values with the same field name.
   *
   * @param table
   *          The name of the table
   * @param key
   *          The record key of the record to write.
   * @param values
   *          A HashMap of field/value pairs to update in the record
   * @return Zero on success, a non-zero error code on error
   */
  public int update(String table, String key, HashMap<String, ByteIterator> values)
  {
    return insert(table, key, values);
  }

  /**
   * Insert a record in the database. Any field/value pairs in the specified
   * values HashMap will be written into the record with the specified record
   * key.
   *
   * @param table
   *          The name of the table
   * @param key
   *          The record key of the record to insert.
   * @param values
   *          A HashMap of field/value pairs to insert in the record
   * @return Zero on success, a non-zero error code on error
   */
  public int insert(String table, String key, HashMap<String, ByteIterator> values)
  {
    if (!_table.equals(table)) {
      try
      {
        client.set_keyspace(table);
        _table = table;
      }
      catch (Exception e)
      {
        e.printStackTrace();
        e.printStackTrace(System.out);
        return Error;
      }
    }

    for (int i = 0; i < OperationRetries; i++)
    {
      if (_debug)
      {
        System.out.println("Inserting key: " + key);
      }

      try
      {
        ByteBuffer wrappedKey = ByteBuffer.wrap(key.getBytes("UTF-8"));

        Column col;
        ColumnOrSuperColumn column;
        for (Map.Entry<String, ByteIterator> entry : values.entrySet())
        {
          col = new Column();
          col.setName(ByteBuffer.wrap(entry.getKey().getBytes("UTF-8")));
          col.setValue(ByteBuffer.wrap(entry.getValue().toArray()));
          col.setTimestamp(System.currentTimeMillis());

          column = new ColumnOrSuperColumn();
          column.setColumn(col);

          mutations.add(new Mutation().setColumn_or_supercolumn(column));
        }

        mutationMap.put(column_family, mutations);
        record.put(wrappedKey, mutationMap);

        client.batch_mutate(record, writeConsistencyLevel);

        mutations.clear();
        mutationMap.clear();
        record.clear();
        
        if (_debug)
        {
           System.out.println("ConsistencyLevel=" + writeConsistencyLevel.toString());
        }

        return Ok;
      } catch (Exception e)
      {
        errorexception = e;
      }
      try
      {
        Thread.sleep(500);
      } catch (InterruptedException e)
      {
      }
    }

    errorexception.printStackTrace();
    errorexception.printStackTrace(System.out);
    return Error;
  }

  /**
   * Delete a record from the database.
   *
   * @param table
   *          The name of the table
   * @param key
   *          The record key of the record to delete.
   * @return Zero on success, a non-zero error code on error
   */
  public int delete(String table, String key)
  {
    if (!_table.equals(table)) {
      try
      {
        client.set_keyspace(table);
        _table = table;
      }
      catch (Exception e)
      {
        e.printStackTrace();
        e.printStackTrace(System.out);
        return Error;
      }
    }

    for (int i = 0; i < OperationRetries; i++)
    {
      try
      {
        client.remove(ByteBuffer.wrap(key.getBytes("UTF-8")),
                      new ColumnPath(column_family),
                      System.currentTimeMillis(),
                      deleteConsistencyLevel);

        if (_debug)
        {
          System.out.println("Delete key: " + key);
          System.out.println("ConsistencyLevel=" + deleteConsistencyLevel.toString());
        }

        return Ok;
      } catch (Exception e)
      {
        errorexception = e;
      }
      try
      {
        Thread.sleep(500);
      } catch (InterruptedException e)
      {
      }
    }
    errorexception.printStackTrace();
    errorexception.printStackTrace(System.out);
    return Error;
  }



  public static void main(String[] args)
  {
  
    CassandraClient10 cli = new CassandraClient10();

    Properties props = new Properties();

    props.setProperty("hosts", args[0]);
    cli.setProperties(props);

    try
    {
      cli.init();
    } catch (Exception e)
    {
      e.printStackTrace();
      System.exit(0);
    }

    HashMap<String, ByteIterator> vals = new HashMap<String, ByteIterator>();
    vals.put("age", new StringByteIterator("57"));
    vals.put("middlename", new StringByteIterator("bradley"));
    vals.put("favoritecolor", new StringByteIterator("blue"));
    int res = cli.insert("usertable", "BrianFrankCooper", vals);
    System.out.println("Result of insert: " + res);

    HashMap<String, ByteIterator> result = new HashMap<String, ByteIterator>();
    HashSet<String> fields = new HashSet<String>();
    fields.add("middlename");
    fields.add("age");
    fields.add("favoritecolor");
    res = cli.read("usertable", "BrianFrankCooper", null, result);
    System.out.println("Result of read: " + res);
    for (String s : result.keySet())
    {
      System.out.println("[" + s + "]=[" + result.get(s) + "]");
    }

    res = cli.delete("usertable", "BrianFrankCooper");
    System.out.println("Result of delete: " + res);
 running=false;   
  }

  /*
   * public static void main(String[] args) throws TException,
   * InvalidRequestException, UnavailableException,
   * UnsupportedEncodingException, NotFoundException {
   *
   *
   *
   * String key_user_id = "1";
   *
   *
   *
   *
   * client.insert("Keyspace1", key_user_id, new ColumnPath("Standard1", null,
   * "age".getBytes("UTF-8")), "24".getBytes("UTF-8"), timestamp,
   * ConsistencyLevel.ONE);
   *
   *
   * // read single column ColumnPath path = new ColumnPath("Standard1", null,
   * "name".getBytes("UTF-8"));
   *
   * System.out.println(client.get("Keyspace1", key_user_id, path,
   * ConsistencyLevel.ONE));
   *
   *
   * // read entire row SlicePredicate predicate = new SlicePredicate(null, new
   * SliceRange(new byte[0], new byte[0], false, 10));
   *
   * ColumnParent parent = new ColumnParent("Standard1", null);
   *
   * List<ColumnOrSuperColumn> results = client.get_slice("Keyspace1",
   * key_user_id, parent, predicate, ConsistencyLevel.ONE);
   *
   * for (ColumnOrSuperColumn result : results) {
   *
   * Column column = result.column;
   *
   * System.out.println(new String(column.name, "UTF-8") + " -> " + new
   * String(column.value, "UTF-8"));
   *
   * }
   *
   *
   *
   *
   * }
   */
}

