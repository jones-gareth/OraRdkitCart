
.. OraRdkitCart documentation master file, created by
   sphinx-quickstart on Thu May 30 16:36:04 2019.
   You can adapt this file completely to your liking, but it should at least
   contain the root `toctree` directive.

.. _home:

OraRdkitCart: Rdkit chemical structure data cartridge for Oracle
================================================================

OraRdkitCart is an Oracle data cartridge/extensible index to allow
substructure and similarity searching using SQL queries on tables
which contain indexed chemical structures.

It uses a Java RMI server and `RDKit <https://www.rdkit.org>`_ wrappers for chemical structure
handling.

This software was developed with funding from `Beacon Discovery <https://www.beacondiscovery.com>`_ and
`Glysade LLC <https://www.glysade.com>`_.

Quick-start
-----------

#. Satisfy :ref:`prerequisites`
#. :ref:`Build <build>` Java code
#. :ref:`Install <install>` cartridge
#. :ref:`Test <test>` installation
#. Run example SQL from the :ref:`manual`

All shell commands in the documentation should be run from project root.

Contents
--------

.. toctree::
   :maxdepth: 3

   background

   prerequisites

   build

   install

   test

   manual

   implementation

   licenses


* :ref:`genindex`
* :ref:`search`
